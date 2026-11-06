package trainer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Speichert Fortschritt und deinen Code im Benutzerordner unter .swing-klausurtrainer.
 * So geht beim Schließen nichts verloren.
 */
public final class Progress {

    private final Path dir;
    private final Path file;
    private final Properties props = new Properties();

    public Progress() {
        this(Path.of(System.getProperty("user.home"), ".swing-klausurtrainer"));
    }

    public Progress(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("fortschritt.properties");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException ignored) {
                // dann eben ohne alten Stand
            }
        }
    }

    public boolean isDone(String id) {
        return Boolean.parseBoolean(props.getProperty("done." + id));
    }

    public void setDone(String id, boolean done) {
        props.setProperty("done." + id, String.valueOf(done));
        save();
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public void put(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    /** Gespeicherter Code einer Datei, oder null, wenn noch nichts gespeichert ist. */
    public String loadCode(String workspaceId, String fileName) {
        Path p = codePath(workspaceId, fileName);
        try {
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void saveCode(String workspaceId, String fileName, String code) {
        Path p = codePath(workspaceId, fileName);
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, code, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // nicht kritisch
        }
    }

    public void deleteCode(String workspaceId, String fileName) {
        try {
            Files.deleteIfExists(codePath(workspaceId, fileName));
        } catch (IOException ignored) {
            // nicht kritisch
        }
    }

    public Path codeDir(String workspaceId) {
        return dir.resolve("code").resolve(workspaceId);
    }

    private Path codePath(String workspaceId, String fileName) {
        return codeDir(workspaceId).resolve(fileName);
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(w, "Swing-Klausurtrainer");
            }
        } catch (IOException ignored) {
            // nicht kritisch
        }
    }
}
