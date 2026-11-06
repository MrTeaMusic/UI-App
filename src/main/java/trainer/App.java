package trainer;

import trainer.compiler.JavaRunner;
import trainer.content.Content;
import trainer.ui.MainWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Startpunkt des Swing-Klausurtrainers. In IntelliJ: Rechtsklick auf diese Datei → Run 'App.main()'. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // dann eben das Standard-Aussehen
            }
            if (!JavaRunner.compilerAvailable()) {
                JOptionPane.showMessageDialog(null,
                        "Es wurde kein Java-Compiler gefunden.\n\n"
                                + "Der Trainer braucht ein JDK (nicht nur eine JRE).\n"
                                + "In IntelliJ: File → Project Structure → SDK → ein JDK 17 oder neuer auswählen.",
                        "JDK fehlt", JOptionPane.ERROR_MESSAGE);
            }
            MainWindow w = new MainWindow(Content.load(), new Progress());
            w.setVisible(true);
        });
    }
}
