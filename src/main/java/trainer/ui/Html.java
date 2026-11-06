package trainer.ui;

import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.Color;

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
                css.addRule("body { font-family: SansSerif; font-size: 13pt; color: #1f2328; margin: 10px 14px; }");
                css.addRule("h1 { font-size: 20pt; margin-top: 4px; margin-bottom: 6px; }");
                css.addRule("h2 { font-size: 16pt; margin-top: 14px; margin-bottom: 4px; }");
                css.addRule("h3 { font-size: 13pt; margin-top: 12px; margin-bottom: 2px; }");
                css.addRule("p { margin-top: 4px; margin-bottom: 6px; }");
                css.addRule("code { font-family: Monospaced; font-size: 12pt; color: #6a1b9a; }");
                css.addRule("pre { font-family: Monospaced; font-size: 12pt; margin: 0; }");
                css.addRule("th { background-color: #eef0f4; text-align: left; }");
                css.addRule(".muted { color: #5f6368; }");
                css.addRule(".cap { color: #5f6368; font-size: 11pt; margin-top: 2px; }");
                css.addRule("a { color: #1a5fb4; }");
                HTMLDocument d = new HTMLDocument(css);
                d.setParser(getParser());
                d.setAsynchronousLoadPriority(-1);
                return d;
            }
        };
        p.setEditorKit(kit);
        p.setBackground(Color.WHITE);
        return p;
    }

    public static String page(String body) {
        return "<html><body>" + body + "</body></html>";
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
