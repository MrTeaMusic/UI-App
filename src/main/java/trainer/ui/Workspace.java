package trainer.ui;

import trainer.content.Content;
import trainer.content.Content.Check;
import trainer.content.Content.SourceFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Alles, was der Editor-Bereich über eine Übung wissen muss. Es gibt drei Arten:
 * eine Code-Aufgabe, eine Altklausur und die freie Spielwiese.
 */
public record Workspace(
        String id,
        String title,
        String introHtml,
        List<SourceFile> starter,
        List<SourceFile> given,
        List<SourceFile> solution,
        String args,
        List<Group> groups,
        boolean exam,
        boolean playground) {

    /** Eine Gruppe von Prüfregeln: bei Aufgaben genau eine, bei Klausuren eine pro Teilaufgabe. */
    public record Group(String id, String title, int points, String textHtml, String scope,
                        List<Check> checks, String hint) { }

    public static Workspace of(Content.Task t) {
        Group g = new Group(t.id(), "Anforderungen", 0, "", null, t.checks(), "");
        return new Workspace(t.id(), t.title(), t.textHtml(), t.starter(), t.given(), t.solution(),
                t.args(), List.of(g), false, false);
    }

    public static Workspace of(Content.Exam e) {
        List<Group> groups = new ArrayList<>();
        int i = 0;
        for (Content.Step s : e.steps()) {
            i++;
            groups.add(new Group(e.id() + "-" + i, s.title(), s.points(), s.textHtml(), s.scope(), s.checks(), s.hint()));
        }
        return new Workspace(e.id(), e.title(), e.introHtml(), e.starter(), List.of(), e.solution(), "",
                groups, true, false);
    }

    public static Workspace createPlayground() {
        String hello = """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hallo Swing!");

                        JFrame frame = new JFrame("Mein erstes Fenster");
                        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                        frame.add(new JLabel("Hier kannst du alles ausprobieren.", SwingConstants.CENTER));
                        frame.setSize(400, 200);
                        frame.setVisible(true);
                    }
                }
                """;
        String intro = "<p>Hier kannst du frei Java schreiben, kompilieren und ausführen. Über <b>+ Datei</b> legst du "
                + "weitere Klassen an, z. B. <code>View.java</code>, <code>Controller.java</code>, <code>Model.java</code> "
                + "und <code>Launcher.java</code> wie in der Klausur.</p>"
                + "<p>Jede <code>public class</code> braucht eine eigene Datei mit genau ihrem Namen.</p>";
        return new Workspace("spielwiese", "Spielwiese", intro, List.of(new SourceFile("Main.java", hello)),
                List.of(), List.of(), "", List.of(), false, true);
    }
}
