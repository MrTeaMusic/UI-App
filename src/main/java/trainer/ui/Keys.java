package trainer.ui;

import java.util.Locale;

/** Beschriftungen der Tastenkürzel, passend zum Betriebssystem (Mac: ⌘ statt Strg). */
public final class Keys {

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    public static final String COMPILE = MAC ? "⌘B" : "Strg+B";
    public static final String RUN = MAC ? "⌘R" : "F5";
    public static final String CHECK = MAC ? "⌘↩" : "Strg+Enter";

    private Keys() {
    }
}
