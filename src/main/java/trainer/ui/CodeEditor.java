package trainer.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein kleiner Code-Editor: Zeilennummern, Farben für Java, automatisches Einrücken,
 * Tab = 4 Leerzeichen, Rückgängig (Strg+Z) und rote Markierung für Fehlerzeilen.
 */
public class CodeEditor extends JScrollPane {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Color ERROR_BG = new Color(255, 225, 225);
    private static final Pattern TOKENS = Pattern.compile(
            "(//[^\n]*|/\\*[\\s\\S]*?(\\*/|$))|(\"(?:\\\\.|[^\"\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\n])*'?)|(@\\w+)|\\b([A-Za-z_]\\w*)\\b|(\\b\\d+(\\.\\d+)?\\b)|(^\\s*#[^\n]*)",
            Pattern.MULTILINE);
    private static final Set<String> KEYWORDS = Set.of(("abstract boolean break byte case catch char class continue default do double else enum "
            + "extends final finally float for if implements import instanceof int interface long new null package private protected public "
            + "return short static super switch this throw throws true false try void while var record").split(" "));

    private final JTextPane text = new JTextPane() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            // kein Zeilenumbruch: lange Zeilen scrollen horizontal
            return getUI().getPreferredSize(this).width <= getParent().getSize().width;
        }
    };
    private final StyledDocument doc = text.getStyledDocument();
    private final UndoManager undo = new UndoManager();
    private final Timer recolor = new Timer(150, e -> highlight());
    private final Set<Integer> errorLines = new HashSet<>();
    private final Gutter gutter = new Gutter();
    private final boolean java;

    private final SimpleAttributeSet plain = style(new Color(0x1f2328), false, false);
    private final SimpleAttributeSet keyword = style(new Color(0x0033b3), true, false);
    private final SimpleAttributeSet string = style(new Color(0x067d17), false, false);
    private final SimpleAttributeSet comment = style(new Color(0x8c8c8c), false, true);
    private final SimpleAttributeSet annotation = style(new Color(0x9e880d), false, false);
    private final SimpleAttributeSet type = style(new Color(0x000080), false, false);
    private final SimpleAttributeSet number = style(new Color(0x1750eb), false, false);

    public CodeEditor(String content, boolean editable, boolean java) {
        this.java = java;
        setViewportView(text);
        setRowHeaderView(gutter);
        setBorder(BorderFactory.createEmptyBorder());
        text.setFont(FONT);
        text.setMargin(new java.awt.Insets(4, 6, 4, 6));
        text.setEditable(editable);
        if (!editable) {
            text.setBackground(new Color(0xf6f7f9));
        }
        setTabs();
        text.setText(content);
        text.setCaretPosition(0);
        highlight();
        recolor.setRepeats(false);
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // nur Farben
            }
        });
        doc.addUndoableEditListener(e -> {
            if (e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent de
                    && de.getType() == DocumentEvent.EventType.CHANGE) {
                return; // Farbänderungen nicht rückgängig machen
            }
            undo.addEdit(e.getEdit());
        });
        installKeys();
    }

    public JTextPane textPane() {
        return text;
    }

    public String getText() {
        return text.getText().replace("\r\n", "\n");
    }

    public void setText(String s) {
        text.setText(s);
        text.setCaretPosition(0);
        undo.discardAllEdits();
        highlight();
    }

    public void addChangeListener(Runnable r) {
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // nur Farben
            }
        });
    }

    /** Markiert Zeilen (ab 1) rot. Leere Menge entfernt alle Markierungen. */
    public void markErrors(Set<Integer> lines) {
        errorLines.clear();
        errorLines.addAll(lines);
        Highlighter h = text.getHighlighter();
        h.removeAllHighlights();
        Element root = doc.getDefaultRootElement();
        for (int line : lines) {
            if (line >= 1 && line <= root.getElementCount()) {
                Element el = root.getElement(line - 1);
                try {
                    h.addHighlight(el.getStartOffset(), Math.max(el.getStartOffset(), el.getEndOffset() - 1), new LinePainter());
                } catch (BadLocationException ignored) {
                    // Zeile gibt es nicht mehr
                }
            }
        }
        gutter.repaint();
        text.repaint();
    }

    /** Setzt den Cursor in Zeile/Spalte (ab 1) und scrollt dorthin. */
    public void goTo(long line, long column) {
        Element root = doc.getDefaultRootElement();
        int l = (int) Math.max(1, Math.min(line, root.getElementCount()));
        Element el = root.getElement(l - 1);
        int pos = (int) Math.min(el.getEndOffset() - 1, el.getStartOffset() + Math.max(0, column - 1));
        text.requestFocusInWindow();
        text.setCaretPosition(pos);
        try {
            Rectangle r = text.modelToView2D(pos).getBounds();
            r.grow(0, 60);
            text.scrollRectToVisible(r);
        } catch (BadLocationException ignored) {
            // egal
        }
    }

    private void changed() {
        recolor.restart();
        if (!errorLines.isEmpty()) {
            SwingUtilities.invokeLater(() -> markErrors(Set.of()));
        }
        gutter.revalidate();
        gutter.repaint();
    }

    private void setTabs() {
        FontMetrics fm = text.getFontMetrics(FONT);
        int w = fm.charWidth(' ') * 4;
        TabStop[] stops = new TabStop[40];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = new TabStop((i + 1) * w);
        }
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setTabSet(a, new TabSet(stops));
        doc.setParagraphAttributes(0, doc.getLength(), a, false);
    }

    private static SimpleAttributeSet style(Color c, boolean bold, boolean italic) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        StyleConstants.setFontFamily(a, FONT.getFamily());
        StyleConstants.setFontSize(a, FONT.getSize());
        return a;
    }

    private void highlight() {
        String s;
        try {
            s = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }
        doc.setCharacterAttributes(0, s.length(), plain, true);
        Matcher m = TOKENS.matcher(s);
        while (m.find()) {
            SimpleAttributeSet a = null;
            if (m.group(1) != null) {
                a = comment;
            } else if (m.group(3) != null) {
                a = string;
            } else if (m.group(4) != null) {
                a = annotation;
            } else if (m.group(5) != null) {
                String id = m.group(5);
                if (KEYWORDS.contains(id)) {
                    a = keyword;
                } else if (Character.isUpperCase(id.charAt(0))) {
                    a = type;
                }
            } else if (m.group(6) != null) {
                a = number;
            } else if (m.group(8) != null && !java) {
                a = comment;
            }
            if (a != null) {
                doc.setCharacterAttributes(m.start(), m.end() - m.start(), a, true);
            }
        }
    }

    private void installKeys() {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "undo", () -> {
            try {
                if (undo.canUndo()) {
                    undo.undo();
                }
            } catch (CannotUndoException ignored) {
                // nichts mehr da
            }
        });
        Runnable redo = () -> {
            try {
                if (undo.canRedo()) {
                    undo.redo();
                }
            } catch (CannotRedoException ignored) {
                // nichts mehr da
            }
        };
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "redo", redo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK), "redo2", redo);
        if (!text.isEditable()) {
            return;
        }
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "indent", () -> text.replaceSelection("    "));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), "outdent", this::outdent);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "newline", this::newline);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, menu), "comment", this::toggleComment);
    }

    private void bind(KeyStroke ks, String name, Runnable r) {
        text.getInputMap(JComponent.WHEN_FOCUSED).put(ks, name);
        text.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    /** Enter: neue Zeile mit gleicher Einrückung, nach { eine Stufe mehr. */
    private void newline() {
        try {
            int pos = text.getCaretPosition();
            Element root = doc.getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(pos));
            String before = doc.getText(line.getStartOffset(), pos - line.getStartOffset());
            String after = doc.getText(pos, Math.max(0, line.getEndOffset() - 1 - pos));
            String indent = before.replaceAll("^(\\s*).*$", "$1");
            if (before.trim().endsWith("{")) {
                if (after.trim().startsWith("}")) {
                    text.replaceSelection("\n" + indent + "    \n" + indent);
                    text.setCaretPosition(pos + 1 + indent.length() + 4);
                } else {
                    text.replaceSelection("\n" + indent + "    ");
                }
            } else {
                text.replaceSelection("\n" + indent);
            }
        } catch (BadLocationException e) {
            text.replaceSelection("\n");
        }
    }

    private void outdent() {
        try {
            int pos = text.getCaretPosition();
            Element root = doc.getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(pos));
            int start = line.getStartOffset();
            String s = doc.getText(start, Math.min(4, line.getEndOffset() - start));
            int n = 0;
            while (n < s.length() && s.charAt(n) == ' ') {
                n++;
            }
            if (n > 0) {
                doc.remove(start, n);
            }
        } catch (BadLocationException ignored) {
            // egal
        }
    }

    /** Strg+/ : markierte Zeilen aus- bzw. einkommentieren. */
    private void toggleComment() {
        try {
            Element root = doc.getDefaultRootElement();
            int first = root.getElementIndex(text.getSelectionStart());
            int last = root.getElementIndex(Math.max(text.getSelectionStart(), text.getSelectionEnd() - 1));
            boolean allCommented = true;
            for (int i = first; i <= last; i++) {
                Element el = root.getElement(i);
                if (!doc.getText(el.getStartOffset(), el.getEndOffset() - el.getStartOffset()).trim().startsWith("//")) {
                    allCommented = false;
                }
            }
            for (int i = last; i >= first; i--) {
                Element el = root.getElement(i);
                String s = doc.getText(el.getStartOffset(), el.getEndOffset() - el.getStartOffset());
                if (allCommented) {
                    int at = s.indexOf("//");
                    doc.remove(el.getStartOffset() + at, s.startsWith("// ", at) ? 3 : 2);
                } else {
                    int at = s.length() - s.stripLeading().length();
                    doc.insertString(el.getStartOffset() + Math.min(at, s.length() - 1), "// ", null);
                }
            }
        } catch (BadLocationException ignored) {
            // egal
        }
    }

    private class LinePainter extends DefaultHighlighter.DefaultHighlightPainter {
        LinePainter() {
            super(ERROR_BG);
        }

        @Override
        public void paint(Graphics g, int p0, int p1, java.awt.Shape bounds, javax.swing.text.JTextComponent c) {
            try {
                Rectangle r = c.modelToView2D(p0).getBounds();
                g.setColor(ERROR_BG);
                g.fillRect(0, r.y, c.getWidth(), r.height);
            } catch (BadLocationException ignored) {
                // egal
            }
        }
    }

    /** Linker Rand mit Zeilennummern; Fehlerzeilen rot. */
    private class Gutter extends JComponent {
        Gutter() {
            setFont(FONT.deriveFont(12f));
        }

        @Override
        public Dimension getPreferredSize() {
            int lines = doc.getDefaultRootElement().getElementCount();
            int digits = Math.max(3, String.valueOf(lines).length());
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.charWidth('0') * digits + 16, text.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Rectangle clip = g.getClipBounds();
            g.setColor(new Color(0xf0f1f4));
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            FontMetrics fm = g.getFontMetrics();
            Element root = doc.getDefaultRootElement();
            for (int i = 0; i < root.getElementCount(); i++) {
                try {
                    Rectangle r = text.modelToView2D(root.getElement(i).getStartOffset()).getBounds();
                    if (r.y + r.height < clip.y || r.y > clip.y + clip.height) {
                        continue;
                    }
                    String num = String.valueOf(i + 1);
                    boolean err = errorLines.contains(i + 1);
                    if (err) {
                        g.setColor(new Color(0xd32f2f));
                        g.fillOval(3, r.y + r.height / 2 - 3, 6, 6);
                    }
                    g.setColor(err ? new Color(0xd32f2f) : new Color(0x9aa0a6));
                    int y = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
                    g.drawString(num, getWidth() - 6 - fm.stringWidth(num), y);
                } catch (BadLocationException ignored) {
                    // egal
                }
            }
        }
    }
}
