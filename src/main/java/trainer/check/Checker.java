package trainer.check;

import trainer.content.Content.Check;
import trainer.content.Content.SourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prüft, ob die geforderten Bausteine im Code vorkommen.
 * Kommentare in .java-Dateien werden vorher entfernt, damit ein auskommentierter
 * Code-Schnipsel nicht als erledigt zählt.
 */
public final class Checker {

    public record Result(Check check, boolean ok) { }

    private Checker() {
    }

    public static List<Result> run(List<Check> checks, List<SourceFile> files) {
        StringBuilder all = new StringBuilder();
        for (SourceFile f : files) {
            all.append(f.isJava() ? stripComments(f.text()) : f.text()).append('\n');
        }
        String text = all.toString();
        List<Result> out = new ArrayList<>();
        for (Check c : checks) {
            out.add(new Result(c, Pattern.compile(c.regex()).matcher(text).find()));
        }
        return out;
    }

    public static boolean allOk(List<Result> results) {
        return results.stream().allMatch(Result::ok);
    }

    /** Entfernt // und /* *&#47; Kommentare, lässt Strings und Zeichen unverändert. */
    public static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            char next = i + 1 < n ? s.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                char q = c;
                out.append(c);
                i++;
                while (i < n && s.charAt(i) != q && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        out.append(s.charAt(i++));
                    }
                    out.append(s.charAt(i++));
                }
                if (i < n && s.charAt(i) == q) {
                    out.append(q);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
