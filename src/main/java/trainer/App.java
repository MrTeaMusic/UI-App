package trainer;

import trainer.compiler.JavaRunner;
import trainer.content.Content;
import trainer.ui.MainWindow;
import trainer.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Startpunkt des Swing-Klausurtrainers. In IntelliJ: Rechtsklick auf diese Datei → Run 'App.main()'. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            trainer.complete.Completion.warmUp();
            Progress progress = new Progress();
            Theme.apply(Boolean.parseBoolean(progress.get("dark", "true")));
            if (!JavaRunner.compilerAvailable()) {
                JOptionPane.showMessageDialog(null,
                        "Es wurde kein Java-Compiler gefunden.\n\n"
                                + "Der Trainer braucht ein JDK (nicht nur eine JRE).\n"
                                + "In IntelliJ: File → Project Structure → SDK → ein JDK 17 oder neuer auswählen.",
                        "JDK fehlt", JOptionPane.ERROR_MESSAGE);
            }
            Content content = Content.load();
            MainWindow w = new MainWindow(content, progress);
            w.setVisible(true);
        });
    }
}
