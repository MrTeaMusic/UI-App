package trainer.ui;

import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/** Hilfen für HTML-Anzeigen (Erklärungen und Aufgabentexte). */
public final class Html {

    private Html() {
    }

    public static JEditorPane pane() {
        JEditorPane p = new JEditorPane();
        p.setEditable(false);
        HTMLEditorKit kit = new HTMLEditorKit() {
            @Override
            public Document createDefaultDocument() {
                // eigenes Stylesheet pro Dokument, damit Tooltips usw. unverändert bleiben
                StyleSheet css = new StyleSheet();
                css.addStyleSheet(getStyleSheet());
                css.addRule("body { font-family: Inter, SansSerif; font-size: 13pt; color: " + Theme.hex(Theme.fg) + "; margin: 14px 22px; }");
                css.addRule("h1 { font-size: 21pt; margin-top: 4px; margin-bottom: 6px; }");
                css.addRule("h2 { font-size: 16pt; margin-top: 16px; margin-bottom: 4px; }");
                css.addRule("h3 { font-size: 13pt; margin-top: 12px; margin-bottom: 2px; }");
                css.addRule("p { margin-top: 4px; margin-bottom: 7px; }");
                css.addRule("li { margin-bottom: 3px; }");
                css.addRule("code { font-family: 'JetBrains Mono', Monospaced; font-size: 12pt; color: " + (Theme.dark ? "#c3a6ff" : "#6a1b9a") + "; }");
                css.addRule("pre { font-family: 'JetBrains Mono', Monospaced; font-size: 12pt; margin: 0; color: " + Theme.hex(Theme.plain) + "; }");
                css.addRule("th { background-color: " + (Theme.dark ? "#2b2e33" : "#eef0f4") + "; text-align: left; }");
                css.addRule("table, td, th { border-color: " + Theme.hex(Theme.border) + "; }");
                css.addRule(".muted { color: " + Theme.hex(Theme.muted) + "; }");
                css.addRule(".cap { color: " + Theme.hex(Theme.muted) + "; font-size: 11pt; margin-top: 3px; }");
                css.addRule("a { color: " + (Theme.dark ? "#7aa7ff" : "#1a5fb4") + "; }");
                HTMLDocument d = new HTMLDocument(css);
                d.setParser(getParser());
                d.setAsynchronousLoadPriority(-1);
                return d;
            }
        };
        p.setEditorKit(kit);
        p.setBackground(Theme.bg);
        p.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, false);
        return p;
    }

    public static String page(String body) {
        return Theme.html("<html><body>" + body + "</body></html>");
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
