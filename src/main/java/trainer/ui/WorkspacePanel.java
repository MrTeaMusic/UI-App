package trainer.ui;

import trainer.Progress;
import trainer.check.Checker;
import trainer.compiler.JavaRunner;
import trainer.compiler.JavaRunner.Problem;
import trainer.content.Content.SourceFile;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Der Arbeitsbereich: links die Aufgabe mit den Anforderungen, rechts der Editor
 * mit einem Tab pro Datei, unten Probleme und Konsole.
 */
public class WorkspacePanel extends JPanel {

    private static final int EXAM_SECONDS = 100 * 60;

    private final Workspace ws;
    private final Progress progress;
    private final Runnable onProgress;

    private final JEditorPane description = Html.pane();
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final Map<String, CodeEditor> editors = new LinkedHashMap<>();
    private final Set<String> givenNames = new HashSet<>();
    private final DefaultListModel<Problem> problemModel = new DefaultListModel<>();
    private final JList<Problem> problemList = new JList<>(problemModel);
    private final JTextPane console = new JTextPane();
    private final JTabbedPane bottom = new JTabbedPane();
    private final JLabel status = new JLabel(" ");
    private final JTextField argsField = new JTextField(8);
    private final JCheckBox autoImports = new JCheckBox("Auto-Imports", true);
    private final JButton runButton = new JButton("▶ Ausführen");
    private final JButton stopButton = new JButton("■ Stopp");
    private final Timer autosave;

    private Map<String, Boolean> lastResults = new LinkedHashMap<>();
    private Process running;

    // Klausur-Uhr
    private final JLabel clock = new JLabel();
    private final Timer clockTimer;
    private int secondsLeft = EXAM_SECONDS;

    public WorkspacePanel(Workspace ws, Progress progress, Runnable onProgress) {
        super(new BorderLayout());
        this.ws = ws;
        this.progress = progress;
        this.onProgress = onProgress;

        argsField.setText(ws.args());
        argsField.setToolTipText("Programmargumente für main (args), durch Leerzeichen getrennt, z. B. de");
        autoImports.setToolTipText("<html>Ergänzt unsichtbar die üblichen Imports (javax.swing.*, java.awt.*, java.awt.event.*, java.beans.*, java.util.*).<br>"
                + "In IntelliJ macht das Alt+Enter für dich. Schalte es aus, um Imports selbst zu üben.</html>");
        autoImports.setSelected(Boolean.parseBoolean(progress.get("autoImports", "true")));
        autoImports.addActionListener(e -> progress.put("autoImports", String.valueOf(autoImports.isSelected())));

        add(buildToolbar(), BorderLayout.NORTH);

        loadFiles();
        description.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                linkClicked(e.getDescription());
            }
        });
        restoreResults();
        renderDescription();

        JScrollPane descScroll = new JScrollPane(description);
        descScroll.setBorder(BorderFactory.createEmptyBorder());
        descScroll.setMinimumSize(new Dimension(260, 100));

        buildBottom();
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, bottom);
        right.setResizeWeight(0.72);
        right.setBorder(BorderFactory.createEmptyBorder());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, descScroll, right);
        split.setResizeWeight(0.33);
        split.setDividerLocation(380);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        add(status, BorderLayout.SOUTH);

        autosave = new Timer(800, e -> saveAll());
        autosave.setRepeats(false);
        for (CodeEditor ed : editors.values()) {
            ed.addChangeListener(autosave::restart);
        }

        clockTimer = new Timer(1000, e -> tick());
        if (ws.exam()) {
            secondsLeft = Integer.parseInt(progress.get("clock." + ws.id(), String.valueOf(EXAM_SECONDS)));
            updateClock();
        }
        installShortcuts();
    }

    /* ------------------------------------------------------------ Aufbau */

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JButton compile = button("Kompilieren", "Nur übersetzen und Fehler anzeigen (Strg+B)", this::compileOnly);
        runButton.setToolTipText("Kompilieren und starten (F5)");
        runButton.addActionListener(e -> runProgram());
        stopButton.setToolTipText("Laufendes Programm beenden");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopProgram());
        tb.add(compile);
        tb.add(runButton);
        tb.add(stopButton);
        if (!ws.playground()) {
            tb.addSeparator();
            JButton check = button("✓ Prüfen", "Kompilieren und Anforderungen prüfen (Strg+Enter)", this::check);
            check.setFont(check.getFont().deriveFont(Font.BOLD));
            tb.add(check);
            tb.add(button("Lösung", "Musterlösung ansehen", this::showSolution));
            tb.add(button("Zurücksetzen", "Zurück zum Ausgangscode", this::reset));
        } else {
            tb.addSeparator();
            tb.add(button("+ Datei", "Neue Datei anlegen", this::addFile));
            tb.add(button("Datei löschen", "Aktuelle Datei löschen", this::deleteFile));
            tb.add(button("Zurücksetzen", "Spielwiese leeren", this::reset));
        }
        tb.add(Box.createHorizontalGlue());
        if (ws.exam()) {
            clock.setFont(clock.getFont().deriveFont(Font.BOLD, 14f));
            clock.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            tb.add(clock);
            tb.add(button("Uhr ▶/❚❚", "Klausur-Uhr starten oder anhalten (100 Minuten)", this::toggleClock));
            tb.add(button("Uhr neu", "Uhr auf 100:00 zurücksetzen", this::resetClock));
            tb.addSeparator();
        }
        tb.add(new JLabel("Argumente: "));
        argsField.setMaximumSize(new Dimension(110, 28));
        tb.add(argsField);
        tb.add(autoImports);
        return tb;
    }

    private static JButton button(String text, String tip, Runnable r) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    private void buildBottom() {
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        problemList.setCellRenderer(new ProblemRenderer());
        problemList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Problem p = problemList.getSelectedValue();
                if (p != null) {
                    jumpTo(p);
                }
            }
        });
        bottom.addTab("Probleme", new JScrollPane(problemList));
        bottom.addTab("Konsole", new JScrollPane(console));
        bottom.setMinimumSize(new Dimension(100, 90));
    }

    private void loadFiles() {
        List<String> names = new ArrayList<>();
        Map<String, String> starter = new LinkedHashMap<>();
        for (SourceFile f : ws.starter()) {
            starter.put(f.name(), f.text());
            names.add(f.name());
        }
        if (ws.playground()) {
            // Spielwiese: alle gespeicherten Dateien laden
            String list = progress.get("files." + ws.id(), "");
            if (!list.isBlank()) {
                names = new ArrayList<>(List.of(list.split(",")));
            }
        }
        for (String name : names) {
            String saved = progress.loadCode(ws.id(), name);
            String text = saved != null ? saved : starter.getOrDefault(name, "public class " + stem(name) + " {\n}\n");
            addEditor(name, text, true);
        }
        for (SourceFile f : ws.given()) {
            givenNames.add(f.name());
            addEditor(f.name(), f.text(), false);
        }
    }

    private void addEditor(String name, String text, boolean editable) {
        CodeEditor ed = new CodeEditor(text, editable, name.endsWith(".java"));
        editors.put(name, ed);
        tabs.addTab(editable ? name : name + " (gegeben)", ed);
        if (!editable) {
            tabs.setToolTipTextAt(tabs.getTabCount() - 1, "Gegebene Datei, nur zum Lesen");
        }
    }

    private void installShortcuts() {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run", this::runProgram);
        shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_B, menu), "compile", this::compileOnly);
        if (!ws.playground()) {
            shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menu), "check", this::check);
        }
    }

    private void shortcut(KeyStroke ks, String name, Runnable r) {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    /* ------------------------------------------------------------ Dateien */

    /** Alle Dateien, wie sie gerade im Editor stehen (bearbeitbare und gegebene). */
    private List<SourceFile> currentFiles() {
        List<SourceFile> out = new ArrayList<>();
        editors.forEach((name, ed) -> out.add(new SourceFile(name, ed.getText())));
        return out;
    }

    private List<SourceFile> editableFiles() {
        List<SourceFile> out = new ArrayList<>();
        editors.forEach((name, ed) -> {
            if (!givenNames.contains(name)) {
                out.add(new SourceFile(name, ed.getText()));
            }
        });
        return out;
    }

    private void saveAll() {
        editors.forEach((name, ed) -> {
            if (!givenNames.contains(name)) {
                progress.saveCode(ws.id(), name, ed.getText());
            }
        });
        if (ws.playground()) {
            progress.put("files." + ws.id(), String.join(",", editors.keySet()));
        }
    }

    public void saveNow() {
        autosave.stop();
        saveAll();
        if (ws.exam()) {
            progress.put("clock." + ws.id(), String.valueOf(secondsLeft));
        }
    }

    private void addFile() {
        String name = JOptionPane.showInputDialog(this, "Name der neuen Klasse (z. B. View):", "Neue Datei", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        name = name.trim();
        if (!name.contains(".")) {
            name += ".java";
        }
        if (!name.matches("[A-Za-z_][\\w]*\\.(java|properties|txt)")) {
            JOptionPane.showMessageDialog(this, "Ungültiger Name. Beispiel: View oder Messages_de.properties");
            return;
        }
        if (editors.containsKey(name)) {
            JOptionPane.showMessageDialog(this, "Die Datei gibt es schon.");
            return;
        }
        String text = name.endsWith(".java") ? "public class " + stem(name) + " {\n\n}\n" : "";
        addEditor(name, text, true);
        editors.get(name).addChangeListener(autosave::restart);
        tabs.setSelectedIndex(tabs.getTabCount() - 1);
        saveAll();
    }

    private void deleteFile() {
        int i = tabs.getSelectedIndex();
        if (i < 0 || editors.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Mindestens eine Datei muss bleiben.");
            return;
        }
        String name = new ArrayList<>(editors.keySet()).get(i);
        if (JOptionPane.showConfirmDialog(this, name + " wirklich löschen?", "Datei löschen",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        editors.remove(name);
        tabs.removeTabAt(i);
        progress.deleteCode(ws.id(), name);
        saveAll();
    }

    private void reset() {
        if (JOptionPane.showConfirmDialog(this, "Deinen Code verwerfen und zum Ausgangscode zurückkehren?",
                "Zurücksetzen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        if (ws.playground()) {
            for (String name : new ArrayList<>(editors.keySet())) {
                progress.deleteCode(ws.id(), name);
            }
            editors.clear();
            tabs.removeAll();
            progress.put("files." + ws.id(), "");
            loadFiles();
            for (CodeEditor ed : editors.values()) {
                ed.addChangeListener(autosave::restart);
            }
        } else {
            for (SourceFile f : ws.starter()) {
                CodeEditor ed = editors.get(f.name());
                if (ed != null) {
                    ed.setText(f.text());
                }
            }
        }
        saveAll();
        lastResults.clear();
        renderDescription();
        problemModel.clear();
        status.setText("Zurückgesetzt.");
    }

    /* ------------------------------------------------------------ Kompilieren, Ausführen, Prüfen */

    private interface AfterCompile {
        void done(JavaRunner.Result r);
    }

    /** Kompiliert im Hintergrund und ruft danach (im Swing-Thread) after auf. */
    private void compile(List<SourceFile> files, AfterCompile after) {
        saveAll();
        status.setText("Kompiliere …");
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        boolean imports = autoImports.isSelected();
        new SwingWorker<JavaRunner.Result, Void>() {
            @Override
            protected JavaRunner.Result doInBackground() throws Exception {
                return JavaRunner.compile(files, imports);
            }

            @Override
            protected void done() {
                setCursor(null);
                try {
                    JavaRunner.Result r = get();
                    showProblems(r);
                    after.done(r);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText("Kompilieren fehlgeschlagen: " + c.getMessage());
                    JOptionPane.showMessageDialog(WorkspacePanel.this, c.getMessage(), "Kompilieren", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showProblems(JavaRunner.Result r) {
        problemModel.clear();
        Map<String, Set<Integer>> lines = new LinkedHashMap<>();
        long errors = 0;
        for (Problem p : r.problems()) {
            problemModel.addElement(p);
            if (p.error()) {
                errors++;
                lines.computeIfAbsent(p.file(), k -> new HashSet<>()).add((int) p.line());
            }
        }
        editors.forEach((name, ed) -> ed.markErrors(lines.getOrDefault(name, Set.of())));
        if (r.ok()) {
            status.setText("Kompiliert ohne Fehler" + (r.problems().isEmpty() ? "." : " (" + r.problems().size() + " Warnung(en))."));
            status.setForeground(new Color(0x2e7d32));
        } else {
            status.setText(errors + " Compiler-Fehler. Klick auf einen Fehler unten springt zur Stelle.");
            status.setForeground(new Color(0xc62828));
            bottom.setSelectedIndex(0);
            if (!problemModel.isEmpty()) {
                problemList.setSelectedIndex(0);
                jumpTo(problemModel.get(0));
            }
        }
    }

    private void jumpTo(Problem p) {
        CodeEditor ed = editors.get(p.file());
        if (ed != null) {
            tabs.setSelectedComponent(ed);
            ed.goTo(p.line(), p.column());
        }
    }

    private void compileOnly() {
        compile(currentFiles(), r -> {
            if (r.ok() && problemModel.isEmpty()) {
                bottom.setSelectedIndex(0);
            }
            JavaRunner.deleteQuietly(r.classDir());
        });
    }

    private void runProgram() {
        stopProgram();
        List<SourceFile> files = currentFiles();
        String main = JavaRunner.findMainClass(files);
        String preview = main == null ? JavaRunner.findPreviewClass(files) : null;
        if (main == null && preview != null) {
            files.add(JavaRunner.previewLauncher(preview));
        }
        compile(files, r -> {
            if (!r.ok()) {
                JavaRunner.deleteQuietly(r.classDir());
                return;
            }
            String cls = main != null ? main : preview != null ? "VorschauStarter" : null;
            if (cls == null) {
                status.setText("Kompiliert. Es gibt keine main-Methode und kein Fenster zum Anzeigen. Nutze ✓ Prüfen.");
                status.setForeground(new Color(0x8a5a00));
                JavaRunner.deleteQuietly(r.classDir());
                return;
            }
            clearConsole();
            bottom.setSelectedIndex(1);
            appendConsole(main != null ? "▶ Starte " + cls + " …\n" : "▶ Vorschau von " + preview + " (es gibt kein main) …\n", Color.GRAY);
            try {
                Path dir = r.classDir();
                running = JavaRunner.run(dir, cls, argsField.getText(),
                        line -> SwingUtilities.invokeLater(() -> appendConsole(line + "\n", null)),
                        line -> SwingUtilities.invokeLater(() -> appendConsole(line + "\n", new Color(0xc62828))),
                        () -> SwingUtilities.invokeLater(() -> programEnded(dir)));
                runButton.setEnabled(false);
                stopButton.setEnabled(true);
                status.setText("Programm läuft. Schließe das Fenster oder drücke Stopp.");
                status.setForeground(new Color(0x1565c0));
            } catch (IOException ex) {
                appendConsole("Konnte das Programm nicht starten: " + ex.getMessage() + "\n", Color.RED);
            }
        });
    }

    private void programEnded(Path dir) {
        int code = running == null ? 0 : safeExit(running);
        appendConsole("■ Programm beendet (Exit-Code " + code + ")\n", Color.GRAY);
        running = null;
        runButton.setEnabled(true);
        stopButton.setEnabled(false);
        status.setText("Programm beendet.");
        status.setForeground(Color.DARK_GRAY);
        JavaRunner.deleteQuietly(dir);
    }

    private static int safeExit(Process p) {
        try {
            return p.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
    }

    public void stopProgram() {
        if (running != null && running.isAlive()) {
            running.descendants().forEach(ProcessHandle::destroyForcibly);
            running.destroyForcibly();
        }
    }

    private void check() {
        List<SourceFile> all = currentFiles();
        boolean hasJava = all.stream().anyMatch(SourceFile::isJava);
        if (!hasJava) {
            evaluate(true);
            return;
        }
        compile(all, r -> {
            JavaRunner.deleteQuietly(r.classDir());
            evaluate(r.ok());
        });
    }

    private void evaluate(boolean compiled) {
        List<SourceFile> files = editableFiles();
        lastResults = new LinkedHashMap<>();
        int points = 0;
        int max = 0;
        int groupsDone = 0;
        for (Workspace.Group g : ws.groups()) {
            List<SourceFile> scope = files;
            if (g.scope() != null) {
                scope = files.stream().filter(f -> f.name().equals(g.scope())).toList();
            }
            List<Checker.Result> res = Checker.run(g.checks(), scope);
            for (int i = 0; i < res.size(); i++) {
                lastResults.put(g.id() + "#" + i, res.get(i).ok());
            }
            // Bei Klausuren zählt jede Teilaufgabe für sich; die Freitext-Aufgabe braucht keinen Compiler
            boolean groupOk = Checker.allOk(res) && (compiled || g.scope() != null);
            max += g.points();
            if (groupOk) {
                groupsDone++;
                points += g.points();
            }
            if (ws.exam()) {
                progress.setDone(g.id(), groupOk);
            }
            progress.put("result." + g.id(), encode(res));
        }
        boolean allDone = groupsDone == ws.groups().size() && compiled;
        progress.setDone(ws.id(), allDone);
        renderDescription();
        onProgress.run();
        if (!compiled) {
            status.setText("Erst die Compiler-Fehler beheben, dann zählt die Aufgabe. Anforderungen siehe links.");
            status.setForeground(new Color(0xc62828));
        } else if (ws.exam()) {
            status.setText("Punkte (geschätzt nach Bausteinen): " + points + " von " + max + ". Erledigte Teilaufgaben: " + groupsDone + "/" + ws.groups().size());
            status.setForeground(allDone ? new Color(0x2e7d32) : new Color(0x8a5a00));
        } else if (allDone) {
            status.setText("Geschafft! Kompiliert und alle Anforderungen erfüllt.");
            status.setForeground(new Color(0x2e7d32));
        } else {
            long missing = lastResults.values().stream().filter(b -> !b).count();
            status.setText("Kompiliert, aber " + missing + " Anforderung(en) fehlen noch. Siehe links.");
            status.setForeground(new Color(0x8a5a00));
        }
    }

    private static String encode(List<Checker.Result> res) {
        StringBuilder sb = new StringBuilder();
        for (Checker.Result r : res) {
            sb.append(r.ok() ? '1' : '0');
        }
        return sb.toString();
    }

    private void restoreResults() {
        for (Workspace.Group g : ws.groups()) {
            String s = progress.get("result." + g.id(), "");
            for (int i = 0; i < s.length() && i < g.checks().size(); i++) {
                lastResults.put(g.id() + "#" + i, s.charAt(i) == '1');
            }
        }
    }

    /* ------------------------------------------------------------ Beschreibung links */

    private void renderDescription() {
        StringBuilder h = new StringBuilder();
        h.append("<h2>").append(Html.esc(ws.title())).append("</h2>");
        if (ws.exam()) {
            h.append(ws.introHtml());
            h.append("<p class='muted'>Wie in der Klausur: 100 Minuten, nur die JDK-Doku. Schreib alle Klassen in die Tabs rechts. "
                    + "<b>✓ Prüfen</b> bewertet jede Teilaufgabe nach den geforderten Bausteinen.</p>");
        } else {
            h.append("<div>").append(ws.introHtml()).append("</div>");
        }
        if (!ws.given().isEmpty()) {
            h.append("<p class='muted'>Gegeben (nur lesen): ");
            h.append(String.join(", ", ws.given().stream().map(SourceFile::name).toList()));
            h.append("</p>");
        }
        int gi = 0;
        for (Workspace.Group g : ws.groups()) {
            gi++;
            if (ws.exam()) {
                boolean done = progress.isDone(g.id());
                h.append("<h3>").append(done ? "<font color='#2e7d32'>✓</font> " : "").append("Teil ").append(gi)
                        .append(": ").append(Html.esc(g.title())).append(" <font color='#777'>(").append(g.points()).append(" P)</font></h3>");
                h.append("<p>").append(g.textHtml()).append("</p>");
            } else if (!g.checks().isEmpty()) {
                h.append("<h3>Anforderungen</h3>");
            }
            if (!g.checks().isEmpty()) {
                h.append("<table cellpadding='2' cellspacing='0'>");
                for (int i = 0; i < g.checks().size(); i++) {
                    Boolean ok = lastResults.get(g.id() + "#" + i);
                    String icon = ok == null ? "<font color='#9aa0a6'>○</font>"
                            : ok ? "<font color='#2e7d32'><b>✓</b></font>" : "<font color='#c62828'><b>✗</b></font>";
                    h.append("<tr><td valign='top' width='18'>").append(icon).append("</td><td><code>")
                            .append(Html.esc(g.checks().get(i).label())).append("</code></td></tr>");
                }
                h.append("</table>");
            }
            if (ws.exam() && g.hint() != null && !g.hint().isBlank()) {
                h.append("<p><a href='hint:").append(gi - 1).append("'>Musterlösung zu Teil ").append(gi).append(" ansehen</a></p>");
            }
        }
        if (!ws.playground()) {
            h.append("<hr><p class='muted'><b>So gehst du vor:</b> Code schreiben → <b>Kompilieren</b> (Strg+B) → Fehler anklicken und beheben → "
                    + "<b>▶ Ausführen</b> (F5) zum Ausprobieren → <b>✓ Prüfen</b> (Strg+Enter).</p>");
        }
        int caret = description.getCaretPosition();
        description.setText(Html.page(h.toString()));
        description.setCaretPosition(Math.min(caret, description.getDocument().getLength()));
    }

    private void linkClicked(String href) {
        if (href != null && href.startsWith("hint:")) {
            Workspace.Group g = ws.groups().get(Integer.parseInt(href.substring(5)));
            CodeEditor ed = new CodeEditor(g.hint(), false, true);
            JDialog d = dialog("Musterlösung: " + g.title(), ed);
            d.setVisible(true);
        }
    }

    /* ------------------------------------------------------------ Lösung */

    private void showSolution() {
        if (ws.solution().isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Erst selbst versuchen hilft am meisten. Lösung trotzdem anzeigen?", "Musterlösung",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        JTabbedPane solTabs = new JTabbedPane();
        for (SourceFile f : ws.solution()) {
            solTabs.addTab(f.name(), new CodeEditor(f.text(), false, f.isJava()));
        }
        JPanel p = new JPanel(new BorderLayout());
        p.add(solTabs, BorderLayout.CENTER);
        JButton take = new JButton("In meinen Editor übernehmen");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(new JLabel("Tipp: Abtippen statt Kopieren bringt in der Klausur mehr."));
        south.add(take);
        p.add(south, BorderLayout.SOUTH);
        JDialog d = dialog("Musterlösung: " + ws.title(), p);
        take.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(d, "Deinen Code durch die Lösung ersetzen?", "Übernehmen",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                for (SourceFile f : ws.solution()) {
                    CodeEditor ed = editors.get(f.name());
                    if (ed != null && !givenNames.contains(f.name())) {
                        ed.setText(f.text());
                    }
                }
                saveAll();
                d.dispose();
            }
        });
        d.setVisible(true);
    }

    private JDialog dialog(String title, Component content) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), title);
        d.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
        d.add(content);
        d.setSize(820, 620);
        d.setLocationRelativeTo(this);
        return d;
    }

    /* ------------------------------------------------------------ Konsole */

    private void clearConsole() {
        console.setText("");
    }

    private void appendConsole(String s, Color c) {
        StyledDocument d = console.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        if (c != null) {
            StyleConstants.setForeground(a, c);
        }
        try {
            d.insertString(d.getLength(), s, a);
        } catch (BadLocationException ignored) {
            // egal
        }
        console.setCaretPosition(d.getLength());
    }

    /* ------------------------------------------------------------ Klausur-Uhr */

    private void toggleClock() {
        if (clockTimer.isRunning()) {
            clockTimer.stop();
        } else {
            clockTimer.start();
        }
        updateClock();
    }

    private void resetClock() {
        clockTimer.stop();
        secondsLeft = EXAM_SECONDS;
        progress.put("clock." + ws.id(), String.valueOf(secondsLeft));
        updateClock();
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft % 30 == 0) {
            progress.put("clock." + ws.id(), String.valueOf(secondsLeft));
        }
        updateClock();
        if (secondsLeft <= 0) {
            clockTimer.stop();
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(this, "Die 100 Minuten sind um. Drück ✓ Prüfen und schau, wie weit du gekommen bist.");
        }
    }

    private void updateClock() {
        int s = Math.max(0, secondsLeft);
        clock.setText((clockTimer.isRunning() ? "⏱ " : "⏸ ") + String.format("%02d:%02d", s / 60, s % 60));
        clock.setForeground(s < 10 * 60 ? new Color(0xc62828) : Color.DARK_GRAY);
    }

    /* ------------------------------------------------------------ Helfer */

    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Löscht alte temporäre Ordner (falls der Trainer abgestürzt ist). */
    static void cleanupTemp() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> s = Files.list(tmp)) {
            s.filter(p -> p.getFileName().toString().startsWith("klausurtrainer")).forEach(JavaRunner::deleteQuietly);
        } catch (IOException ignored) {
            // egal
        }
    }

    private static class ProblemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            Problem p = (Problem) value;
            String msg = Html.esc(p.message().split("\n")[0]);
            String detail = p.message().contains("\n") ? Html.esc(p.message().substring(p.message().indexOf('\n') + 1).trim()) : "";
            String color = p.error() ? "#c62828" : "#8a5a00";
            String html = "<html><b><font color='" + color + "'>" + (p.error() ? "Fehler" : "Warnung") + "</font></b> "
                    + Html.esc(p.file()) + ":" + p.line() + " &nbsp;<code>" + msg + "</code>"
                    + (detail.isEmpty() ? "" : " <font color='#777'>" + detail.replace("\n", " · ") + "</font>")
                    + (p.hint().isEmpty() ? "" : "<br><font color='#1a5fb4'>→ " + Html.esc(p.hint()) + "</font>") + "</html>";
            JLabel l = (JLabel) super.getListCellRendererComponent(list, html, index, sel, focus);
            l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return l;
        }
    }
}
