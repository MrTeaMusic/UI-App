package trainer.ui;

import trainer.Progress;
import trainer.content.Content;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/** Hauptfenster: links der Lernpfad als Baum, rechts Erklärung oder Editor. */
public class MainWindow extends JFrame {

    public static final LocalDate EXAM_DATE = LocalDate.of(2026, 12, 14);

    private final Content content;
    private final Progress progress;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Swing-Klausurtrainer");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final JPanel right = new JPanel(new BorderLayout());
    private final Map<String, WorkspacePanel> workspaces = new HashMap<>();
    private String currentKey = "home";

    /** Was ein Knoten im Baum darstellt. */
    private record Item(String kind, String label, Object data) {
        @Override
        public String toString() {
            return label;
        }
    }

    public MainWindow(Content content, Progress progress) {
        this.content = content;
        this.progress = progress;
        setTitle("Swing-Klausurtrainer – User Interaction");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownWorkspaces();
                dispose();
                System.exit(0);
            }
        });
        buildTree();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(28);
        tree.setBackground(Theme.panel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        tree.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        tree.addTreeSelectionListener(e -> {
            Object n = tree.getLastSelectedPathComponent();
            if (n instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Item item) {
                open(item);
            }
        });
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.panel);
        side.setMinimumSize(new Dimension(220, 100));
        side.add(sidebarHeader(), BorderLayout.NORTH);
        side.add(treeScroll, BorderLayout.CENTER);
        side.add(sidebarFooter(), BorderLayout.SOUTH);
        right.setBackground(Theme.bg);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, right);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(340);
        add(split);
        setSize(1400, 880);
        setLocationRelativeTo(null);
        tree.setSelectionRow(0);
    }

    /** Kopf der Seitenleiste: Name und Countdown bis zur Klausur. */
    private JComponent sidebarHeader() {
        JPanel p = new JPanel();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setBackground(Theme.panel);
        p.setBorder(BorderFactory.createEmptyBorder(16, 18, 10, 18));
        JLabel title = new JLabel("Swing-Klausurtrainer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        title.setForeground(Theme.fg);
        long days = ChronoUnit.DAYS.between(LocalDate.now(), EXAM_DATE);
        JLabel sub = new JLabel(days > 0 ? "Noch " + days + " Tage bis zur Klausur" : days == 0 ? "Heute ist Klausur – viel Erfolg!" : "User Interaction");
        sub.setForeground(Theme.accent);
        sub.setFont(sub.getFont().deriveFont(Font.BOLD, 12f));
        sub.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        p.add(title);
        p.add(sub);
        return p;
    }

    /** Fuß der Seitenleiste: Umschalter für Hell und Dunkel. */
    private JComponent sidebarFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.panel);
        p.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
        javax.swing.JButton toggle = new javax.swing.JButton(Theme.dark ? "☀  Helles Design" : "☾  Dunkles Design");
        toggle.setFocusable(false);
        toggle.addActionListener(e -> switchTheme());
        p.add(toggle, BorderLayout.CENTER);
        return p;
    }

    /** Wechselt das Design. Das Fenster wird neu aufgebaut, dein Code ist vorher gespeichert. */
    private void switchTheme() {
        shutdownWorkspaces();
        boolean dark = !Theme.dark;
        progress.put("dark", String.valueOf(dark));
        Theme.apply(dark);
        MainWindow w = new MainWindow(content, progress);
        w.setBounds(getBounds());
        w.setExtendedState(getExtendedState());
        w.select(currentKey);
        dispose();
        w.setVisible(true);
    }

    private void shutdownWorkspaces() {
        for (WorkspacePanel w : workspaces.values()) {
            w.saveNow();
            w.stopProgram();
        }
    }

    private static String keyOf(Item it) {
        if (it.data() instanceof Content.Task t) {
            return t.id();
        }
        if (it.data() instanceof Content.Exam ex) {
            return ex.id();
        }
        if (it.data() instanceof Content.Module m && it.kind().equals("lesson")) {
            return m.id();
        }
        return it.kind();
    }

    private void buildTree() {
        root.add(new DefaultMutableTreeNode(new Item("home", "Start und Fortschritt", null)));
        root.add(new DefaultMutableTreeNode(new Item("play", "Spielwiese (freies Üben)", null)));
        DefaultMutableTreeNode learn = new DefaultMutableTreeNode(new Item("folder", "Lernpfad", null));
        java.util.List<DefaultMutableTreeNode> modules = new java.util.ArrayList<>();
        int i = 0;
        for (Content.Module m : content.modules()) {
            DefaultMutableTreeNode mn = new DefaultMutableTreeNode(new Item("module", i++ + " · " + m.title(), m));
            mn.add(new DefaultMutableTreeNode(new Item("lesson", "Erklärung", m)));
            for (Content.Task t : m.tasks()) {
                mn.add(new DefaultMutableTreeNode(new Item("task", t.title(), t)));
            }
            learn.add(mn);
            modules.add(mn);
        }
        root.add(learn);
        DefaultMutableTreeNode exams = new DefaultMutableTreeNode(new Item("folder", "Altklausuren (100 Minuten)", null));
        for (Content.Exam e : content.exams()) {
            exams.add(new DefaultMutableTreeNode(new Item("exam", e.title(), e)));
        }
        root.add(exams);
        treeModel.reload();
        tree.expandPath(new TreePath(learn.getPath()));
        tree.expandPath(new TreePath(exams.getPath()));
        // Kapitel aufklappen, damit die Code-Aufgaben sofort sichtbar sind
        for (DefaultMutableTreeNode mn : modules) {
            tree.expandPath(new TreePath(mn.getPath()));
        }
    }

    private void open(Item item) {
        currentKey = keyOf(item);
        Component view = switch (item.kind()) {
            case "home" -> htmlView(homeHtml());
            case "play" -> workspace(Workspace.createPlayground());
            case "module", "lesson" -> htmlView(((Content.Module) item.data()).lessonHtml());
            case "task" -> workspace(Workspace.of((Content.Task) item.data()));
            case "exam" -> workspace(Workspace.of((Content.Exam) item.data()));
            default -> null;
        };
        if (view == null) {
            return;
        }
        right.removeAll();
        right.add(view, BorderLayout.CENTER);
        right.revalidate();
        right.repaint();
    }

    private Component workspace(Workspace ws) {
        return workspaces.computeIfAbsent(ws.id(), id -> new WorkspacePanel(ws, progress, this::progressChanged));
    }

    private void progressChanged() {
        tree.repaint();
    }

    private Component htmlView(String html) {
        JEditorPane p = Html.pane();
        p.setText(Html.page(html));
        p.setCaretPosition(0);
        p.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || e.getDescription() == null) {
                return;
            }
            if (e.getDescription().startsWith("open:")) {
                select(e.getDescription().substring(5));
            } else if (e.getDescription().startsWith("code:")) {
                showCode(new String(java.util.Base64.getUrlDecoder().decode(e.getDescription().substring(5)),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        JScrollPane s = new JScrollPane(p);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }

    /** Zeigt die Lösung eines Lückentexts in einem eigenen Fenster. */
    private void showCode(String code) {
        javax.swing.JDialog d = new javax.swing.JDialog(this, "Lösung");
        d.add(new CodeEditor(code, false, true));
        d.setSize(760, 520);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    /** Wählt einen Baumknoten nach Art bzw. Id aus (für Links auf der Startseite). */
    void select(String key) {
        Enumeration<?> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) en.nextElement();
            if (n.getUserObject() instanceof Item it) {
                String id = keyOf(it);
                if (id.equals(key)) {
                    TreePath p = new TreePath(n.getPath());
                    tree.setSelectionPath(p);
                    java.awt.Rectangle r = tree.getPathBounds(p);
                    if (r != null) {
                        // nur senkrecht scrollen, sonst verschwindet die Einrückung links
                        tree.scrollRectToVisible(new java.awt.Rectangle(0, r.y, 1, r.height));
                    }
                    return;
                }
            }
        }
    }

    private String homeHtml() {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), EXAM_DATE);
        int total = 0;
        int done = 0;
        StringBuilder rows = new StringBuilder();
        int i = 0;
        for (Content.Module m : content.modules()) {
            int d = (int) m.tasks().stream().filter(t -> progress.isDone(t.id())).count();
            total += m.tasks().size();
            done += d;
            rows.append("<tr><td>").append(i++).append("</td><td><a href='open:").append(m.id()).append("'>")
                    .append(Html.esc(m.title())).append("</a></td><td>")
                    .append(m.tasks().isEmpty() ? "–" : d + " / " + m.tasks().size()).append("</td></tr>");
        }
        StringBuilder ex = new StringBuilder();
        for (Content.Exam e : content.exams()) {
            int pts = 0;
            int max = 0;
            for (int s = 0; s < e.steps().size(); s++) {
                max += e.steps().get(s).points();
                if (progress.isDone(e.id() + "-" + (s + 1))) {
                    pts += e.steps().get(s).points();
                }
            }
            ex.append("<tr><td><a href='open:").append(e.id()).append("'>").append(Html.esc(e.title()))
                    .append("</a></td><td>").append(pts).append(" / ").append(max).append(" P</td></tr>");
        }
        String countdown = days > 0 ? "Noch <b>" + days + " Tage</b> bis zur Klausur am 14.12.2026."
                : days == 0 ? "<b>Heute ist Klausur. Viel Erfolg!</b>" : "Die Klausur am 14.12.2026 ist vorbei.";
        return "<h1>Swing-Klausurtrainer</h1>"
                + "<p>" + countdown + " Aufgaben erledigt: <b>" + done + " von " + total + "</b>.</p>"
                + "<h2>So funktioniert's</h2>"
                + "<ol>"
                + "<li><b>Erklärung lesen:</b> Jedes Kapitel links beginnt mit einer Erklärung mit Beispielen.</li>"
                + "<li><b>Code schreiben:</b> Unter jedem Kapitel liegen Aufgaben. Du schreibst echten Java-Code im Editor.</li>"
                + "<li><b>Kompilieren</b> (" + Keys.COMPILE + "): Der echte Java-Compiler prüft deinen Code. Fehler werden rot markiert und auf Deutsch erklärt.</li>"
                + "<li><b>▶ Ausführen</b> (" + Keys.RUN + "): Dein Programm startet, Swing-Fenster öffnen sich wirklich. "
                + "Hat eine Klasse kein main, zeigt der Trainer ein Fenster oder Panel als Vorschau.</li>"
                + "<li><b>✓ Prüfen</b> (" + Keys.CHECK + "): Kompiliert und prüft, ob alle geforderten Bausteine da sind. Dann gibt es den Haken.</li>"
                + "</ol>"
                + "<p>Dein Code wird automatisch gespeichert (im Benutzerordner unter <code>.swing-klausurtrainer</code>).</p>"
                + "<h2>Lernpfad</h2>"
                + "<table cellpadding='4' cellspacing='0' border='1'><tr><th>#</th><th>Kapitel</th><th>Aufgaben</th></tr>" + rows + "</table>"
                + "<h2>Altklausuren</h2>"
                + "<p>Erst wenn die Kapitel sitzen: echte Klausuren mit 100-Minuten-Uhr. Gegebene Klassen sind schon als Tabs da, "
                + "jede Teilaufgabe wird einzeln bewertet.</p>"
                + "<table cellpadding='4' cellspacing='0' border='1'><tr><th>Klausur</th><th>Punkte (geschätzt)</th></tr>" + ex + "</table>"
                + "<h2>Plan bis zur Klausur</h2>"
                + "<ul>"
                + "<li><b>Woche 1:</b> Kapitel 0 bis 3 (Java-Grundlagen, Fenster, Layouts, Steuerelemente)</li>"
                + "<li><b>Woche 2:</b> Kapitel 4 bis 7 (Events, MVC, Zeichnen, Validierung und i18n)</li>"
                + "<li><b>Woche 3:</b> Kapitel 8 (Theorie) und die erste Altklausur mit Uhr</li>"
                + "<li><b>Woche 4 bis 5:</b> Die anderen Altklausuren, Lücken wiederholen, zum Schluss alles noch einmal in IntelliJ</li>"
                + "</ul>"
                + "<p><a href='open:play'>Zur Spielwiese</a> &nbsp;·&nbsp; <a href='open:m0'>Mit Kapitel 0 anfangen</a></p>";
    }

    private class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
            JLabel l = (JLabel) super.getTreeCellRendererComponent(t, value, sel, exp, leaf, row, focus);
            l.setIcon(null);
            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (!(o instanceof Item it)) {
                return l;
            }
            l.setFont(t.getFont().deriveFont(it.kind().equals("folder") || it.kind().equals("module") ? Font.BOLD : Font.PLAIN));
            switch (it.kind()) {
                case "task" -> {
                    boolean d = progress.isDone(((Content.Task) it.data()).id());
                    l.setText((d ? "✓ " : "○ ") + it.label());
                    if (!sel) {
                        l.setForeground(d ? Theme.ok : Theme.fg);
                    }
                }
                case "module" -> {
                    Content.Module m = (Content.Module) it.data();
                    long d = m.tasks().stream().filter(x -> progress.isDone(x.id())).count();
                    l.setText(it.label() + (m.tasks().isEmpty() ? "" : "  (" + d + "/" + m.tasks().size() + ")"));
                }
                case "exam" -> {
                    Content.Exam e = (Content.Exam) it.data();
                    long d = java.util.stream.IntStream.rangeClosed(1, e.steps().size())
                            .filter(k -> progress.isDone(e.id() + "-" + k)).count();
                    l.setText(it.label() + "  (" + d + "/" + e.steps().size() + ")");
                }
                case "lesson" -> l.setText("» " + it.label());
                default -> { }
            }
            return l;
        }
    }
}
