package trainer;

import trainer.check.Checker;
import trainer.compiler.JavaRunner;
import trainer.content.Content;
import trainer.content.Content.SourceFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft alle Inhalte: Jede Musterlösung muss kompilieren und ihre eigenen Checks bestehen,
 * jeder Ausgangscode darf die Checks noch nicht komplett bestehen.
 * Start: mvn -q compile exec:java -Dexec.mainClass=trainer.SelfTest  (oder in IntelliJ: Run 'SelfTest.main()')
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Content c = Content.load();
        int fails = 0;
        int count = 0;
        for (Content.Module m : c.modules()) {
            for (Content.Task t : m.tasks()) {
                count++;
                List<SourceFile> sol = new ArrayList<>(t.solution());
                List<SourceFile> all = new ArrayList<>(sol);
                all.addAll(t.given());
                JavaRunner.Result r = JavaRunner.compile(all, true);
                JavaRunner.deleteQuietly(r.classDir());
                List<Checker.Result> res = Checker.run(t.checks(), sol);
                List<String> missing = res.stream().filter(x -> !x.ok()).map(x -> x.check().label()).toList();
                if (!r.ok() || !missing.isEmpty()) {
                    fails++;
                    System.out.println("FEHLER " + t.id() + " " + t.title());
                    r.problems().stream().filter(JavaRunner.Problem::error).forEach(p -> System.out.println("   " + p));
                    missing.forEach(x -> System.out.println("   Check fehlt: " + x));
                }
                if (!t.starter().stream().allMatch(f -> sol.stream().anyMatch(s -> s.name().equals(f.name())))) {
                    fails++;
                    System.out.println("FEHLER " + t.id() + ": Starter- und Lösungsdateien passen nicht zusammen");
                }
                List<SourceFile> start = new ArrayList<>(t.starter());
                boolean starterPasses = !t.checks().isEmpty() && Checker.allOk(Checker.run(t.checks(), start));
                start.addAll(t.given());
                JavaRunner.Result sr = JavaRunner.compile(start, true);
                JavaRunner.deleteQuietly(sr.classDir());
                if (starterPasses && sr.ok()) {
                    fails++;
                    System.out.println("FEHLER " + t.id() + ": Ausgangscode wäre schon erledigt");
                }
            }
        }
        for (Content.Exam e : c.exams()) {
            count++;
            JavaRunner.Result r = JavaRunner.compile(e.solution(), true);
            JavaRunner.deleteQuietly(r.classDir());
            if (!r.ok()) {
                fails++;
                System.out.println("FEHLER " + e.id() + ": Lösung kompiliert nicht");
                r.problems().forEach(p -> System.out.println("   " + p));
            }
            JavaRunner.Result s = JavaRunner.compile(e.starter(), true);
            JavaRunner.deleteQuietly(s.classDir());
            if (!s.ok()) {
                // Kann gewollt sein: In 2023 ist JDiagram unvollständig gegeben und muss erst erweitert werden.
                System.out.println("Hinweis " + e.id() + ": Ausgangsdateien kompilieren noch nicht ("
                        + s.problems().stream().filter(JavaRunner.Problem::error).count() + " Fehler)");
            }
            for (Content.Step st : e.steps()) {
                List<SourceFile> scope = st.scope() == null ? e.solution()
                        : e.solution().stream().filter(f -> f.name().equals(st.scope())).toList();
                for (Checker.Result x : Checker.run(st.checks(), scope)) {
                    if (!x.ok()) {
                        fails++;
                        System.out.println("FEHLER " + e.id() + " / " + st.title() + ": Check fehlt: " + x.check().label());
                    }
                }
            }
        }
        System.out.println(count + " Aufgaben/Klausuren geprüft, " + fails + " Fehler.");
        System.exit(fails == 0 ? 0 : 1);
    }
}
