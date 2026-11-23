package trainer.ui;

import trainer.complete.Completion;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * Autovervollständigung für den Editor: erscheint nach einem Punkt oder nach zwei getippten
 * Buchstaben, mit Strg+Leertaste jederzeit. Pfeiltasten wählen, Enter oder Tab übernimmt, Esc schließt.
 */
final class CompletionPopup {

    private final JTextPane text;
    private final Supplier<List<String>> context;
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<Completion.Item> model = new DefaultListModel<>();
    private final JList<Completion.Item> list = new JList<>(model);
    private int replaceFrom;
    private boolean inserting;

    CompletionPopup(JTextPane text, Supplier<List<String>> context) {
        this.text = text;
        this.context = context;
        list.setFocusable(false);
        list.setCellRenderer(new Renderer());
        list.setVisibleRowCount(10);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    accept();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setFocusable(false);
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(Theme.border));
        popup.setLayout(new BorderLayout());
        popup.add(scroll, BorderLayout.CENTER);

        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (inserting) {
                    return;
                }
                if (e.getLength() != 1) {
                    hide();
                    return;
                }
                char c;
                try {
                    c = text.getDocument().getText(e.getOffset(), 1).charAt(0);
                } catch (BadLocationException ex) {
                    return;
                }
                SwingUtilities.invokeLater(() -> typed(c));
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!inserting && popup.isVisible()) {
                    SwingUtilities.invokeLater(() -> update(true));
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // nur Farben
            }
        });
        // Tasten abfangen, solange die Liste offen ist (KeyListener laufen vor den Tastenbelegungen)
        text.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) {
                    return;
                }
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> move(1);
                    case KeyEvent.VK_UP -> move(-1);
                    case KeyEvent.VK_PAGE_DOWN -> move(list.getVisibleRowCount());
                    case KeyEvent.VK_PAGE_UP -> move(-list.getVisibleRowCount());
                    case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> accept();
                    case KeyEvent.VK_ESCAPE -> hide();
                    case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END -> {
                        hide();
                        return;
                    }
                    default -> {
                        return;
                    }
                }
                e.consume();
            }
        });
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                hide();
            }
        });
        text.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                hide();
            }
        });
        text.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "complete");
        text.getActionMap().put("complete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                update(false);
            }
        });
    }

    private void typed(char c) {
        if (c == '.') {
            update(true);
        } else if (Character.isJavaIdentifierPart(c)) {
            if (popup.isVisible() || currentPrefixLength() >= 2) {
                update(true);
            }
        } else {
            hide();
        }
    }

    private int currentPrefixLength() {
        int caret = text.getCaretPosition();
        String s = text.getText().replace("\r\n", "\n");
        int i = caret;
        while (i > 0 && i <= s.length() && Character.isJavaIdentifierPart(s.charAt(i - 1))) {
            i--;
        }
        // Zahlen allein lösen nichts aus
        return i < caret && Character.isJavaIdentifierStart(s.charAt(i)) ? caret - i : 0;
    }

    /** Berechnet die Vorschläge neu und zeigt sie an. auto = durch Tippen ausgelöst. */
    private void update(boolean auto) {
        String s = text.getText().replace("\r\n", "\n");
        int caret = Math.min(text.getCaretPosition(), s.length());
        if (auto && inCommentOrString(s, caret)) {
            hide();
            return;
        }
        Completion.Result r = Completion.complete(s, caret, context.get());
        boolean afterDot = r.replaceFrom() > 0 && s.charAt(r.replaceFrom() - 1) == '.';
        if (r.items().isEmpty() || (auto && r.prefix().isEmpty() && !afterDot)
                || (auto && r.items().size() == 1 && r.items().get(0).insert().equals(r.prefix()))) {
            hide();
            return;
        }
        replaceFrom = r.replaceFrom();
        model.clear();
        r.items().forEach(model::addElement);
        list.setSelectedIndex(0);
        list.ensureIndexIsVisible(0);
        try {
            Rectangle at = text.modelToView2D(replaceFrom).getBounds();
            int rows = Math.min(10, model.size());
            popup.setPopupSize(new Dimension(520, rows * 26 + 6));
            if (popup.isVisible()) {
                popup.setLocation(locationOnScreen(at));
            } else {
                popup.show(text, at.x - 4, at.y + at.height + 2);
            }
        } catch (BadLocationException e) {
            hide();
        }
    }

    private java.awt.Point locationOnScreen(Rectangle at) {
        java.awt.Point p = new java.awt.Point(at.x - 4, at.y + at.height + 2);
        SwingUtilities.convertPointToScreen(p, text);
        return p;
    }

    private void move(int delta) {
        int i = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void accept() {
        Completion.Item it = list.getSelectedValue();
        hide();
        if (it == null) {
            return;
        }
        int caret = text.getCaretPosition();
        String indent = lineIndent(caret);
        String ins = it.insert().replace("\n", "\n" + indent);
        int mark = ins.indexOf(Completion.CARET);
        ins = ins.replace(String.valueOf(Completion.CARET), "");
        inserting = true;
        try {
            text.getDocument().remove(replaceFrom, caret - replaceFrom);
            text.getDocument().insertString(replaceFrom, ins, null);
            text.setCaretPosition(replaceFrom + (mark >= 0 ? mark : ins.length()));
        } catch (BadLocationException ignored) {
            // egal
        } finally {
            inserting = false;
        }
    }

    private String lineIndent(int pos) {
        try {
            Element root = text.getDocument().getDefaultRootElement();
            Element line = root.getElement(root.getElementIndex(pos));
            String s = text.getDocument().getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset());
            return s.replaceAll("^([ \\t]*).*[\\s\\S]*$", "$1");
        } catch (BadLocationException e) {
            return "";
        }
    }

    void hide() {
        if (popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    /** Grobe Prüfung: steht der Cursor in einem Kommentar oder String dieser Zeile? */
    private static boolean inCommentOrString(String s, int caret) {
        int ls = s.lastIndexOf('\n', caret - 1) + 1;
        String line = s.substring(ls, caret);
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inStr) {
                i++;
            } else if (c == '"') {
                inStr = !inStr;
            } else if (!inStr && c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                return true;
            }
        }
        return inStr || line.trim().startsWith("*");
    }

    /** Eine Zeile der Liste: Art (m, f, v, C, k), Name mit Parametern, rechts der Typ. */
    private static final class Renderer extends JPanel implements ListCellRenderer<Completion.Item> {
        private final JLabel kind = new JLabel();
        private final JLabel label = new JLabel();
        private final JLabel detail = new JLabel();

        Renderer() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 10));
            kind.setPreferredSize(new Dimension(18, 18));
            kind.setHorizontalAlignment(JLabel.CENTER);
            kind.setFont(Theme.mono(12).deriveFont(Font.BOLD));
            label.setFont(Theme.mono(13));
            detail.setFont(Theme.mono(12));
            add(kind, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            add(detail, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Completion.Item> list, Completion.Item it,
                                                      int index, boolean selected, boolean focus) {
            String k = switch (it.kind()) {
                case METHOD -> "m";
                case FIELD -> "f";
                case VARIABLE -> "v";
                case CLASS -> "C";
                case KEYWORD -> "k";
                case SNIPPET -> "»";
            };
            kind.setText(k);
            kind.setForeground(switch (it.kind()) {
                case METHOD -> Theme.keyword;
                case FIELD, VARIABLE -> Theme.number;
                case CLASS -> Theme.accent;
                default -> Theme.muted;
            });
            label.setText(it.label());
            detail.setText(it.detail());
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : Theme.fg);
            detail.setForeground(selected ? list.getSelectionForeground() : Theme.muted);
            return this;
        }
    }
}
