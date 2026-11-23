package trainer.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Farben und Schriften der App, hell oder dunkel. Das Aussehen kommt von FlatLaf.
 * Die Lerninhalte (HTML) sind mit hellen Farben geschrieben; im Dark Mode werden sie umgefärbt.
 */
public final class Theme {

    public static boolean dark;

    public static Color bg;
    public static Color panel;
    public static Color fg;
    public static Color muted;
    public static Color border;
    public static Color accent;
    public static Color ok;
    public static Color err;
    public static Color warn;
    public static Color editorBg;
    public static Color readOnlyBg;
    public static Color gutterBg;
    public static Color gutterFg;
    public static Color errorLine;

    // Syntaxfarben
    public static Color plain;
    public static Color keyword;
    public static Color string;
    public static Color comment;
    public static Color annotation;
    public static Color type;
    public static Color number;

    public static final String UI_FONT = "Inter";
    public static final String MONO_FONT = "JetBrains Mono";

    /** Helle Farben in den Inhalten → passende dunkle Farben. */
    private static final Map<String, String> DARK_MAP = new LinkedHashMap<>();

    static {
        // Kästen
        DARK_MAP.put("#eef4ff", "#1f2a3d");
        DARK_MAP.put("#eaf7ee", "#1d3024");
        DARK_MAP.put("#fff3e0", "#3a2c1a");
        DARK_MAP.put("#fff8e1", "#36301c");
        DARK_MAP.put("#f3f4f7", "#26282c");
        DARK_MAP.put("#eef0f4", "#2b2e33");
        // Code-Farben in den Erklärungen
        DARK_MAP.put("#7a7f87", "#7f848e");
        DARK_MAP.put("#9c6500", "#d4b45f");
        DARK_MAP.put("#0033b3", "#cf8e6d");
        DARK_MAP.put("#6a1b9a", "#c3a6ff");
        // Texte und Zustände
        DARK_MAP.put("#1f2328", "#dfe1e5");
        DARK_MAP.put("#5f6368", "#9da0a8");
        DARK_MAP.put("#777", "#9da0a8");
        DARK_MAP.put("#9aa0a6", "#6f737a");
        DARK_MAP.put("#1a5fb4", "#7aa7ff");
        DARK_MAP.put("#2e7d32", "#6cc070");
        DARK_MAP.put("#c62828", "#ff6b78");
        DARK_MAP.put("#8a5a00", "#e5b567");
    }

    private Theme() {
    }

    /** Setzt Look-and-Feel, Schriften und Farben. Danach müssen Fenster neu aufgebaut werden. */
    public static void apply(boolean darkMode) {
        dark = darkMode;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
        FlatInterFont.install();
        FlatJetBrainsMonoFont.install();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);

        if (dark) {
            bg = rgb(0x1e1f22);
            panel = rgb(0x2b2d30);
            fg = rgb(0xdfe1e5);
            muted = rgb(0x9da0a8);
            border = rgb(0x393b40);
            accent = rgb(0x548af7);
            ok = rgb(0x6cc070);
            err = rgb(0xff6b78);
            warn = rgb(0xe5b567);
            editorBg = rgb(0x1e1f22);
            readOnlyBg = rgb(0x24262a);
            gutterBg = rgb(0x1e1f22);
            gutterFg = rgb(0x5f636b);
            errorLine = rgb(0x47262b);
            plain = rgb(0xbcbec4);
            keyword = rgb(0xcf8e6d);
            string = rgb(0x6aab73);
            comment = rgb(0x7a7e85);
            annotation = rgb(0xb3ae60);
            type = rgb(0xc8ccd4);
            number = rgb(0x2aacb8);
        } else {
            bg = rgb(0xffffff);
            panel = rgb(0xf5f6f8);
            fg = rgb(0x1f2328);
            muted = rgb(0x5f6368);
            border = rgb(0xdfe1e5);
            accent = rgb(0x2f6fde);
            ok = rgb(0x1f883d);
            err = rgb(0xcf222e);
            warn = rgb(0x9a6700);
            editorBg = rgb(0xffffff);
            readOnlyBg = rgb(0xf6f7f9);
            gutterBg = rgb(0xffffff);
            gutterFg = rgb(0xadb1b8);
            errorLine = rgb(0xffebe9);
            plain = rgb(0x1f2328);
            keyword = rgb(0x0033b3);
            string = rgb(0x067d17);
            comment = rgb(0x8c8c8c);
            annotation = rgb(0x9e880d);
            type = rgb(0x000080);
            number = rgb(0x1750eb);
        }

        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("Tree.rowHeight", 28);
        UIManager.put("Tree.showDefaultIcons", false);
        UIManager.put("Tree.selectionArc", 8);
        UIManager.put("Tree.selectionInsets", new java.awt.Insets(0, 2, 0, 2));
        UIManager.put("TabbedPane.tabHeight", 32);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.selectedBackground", editorBg);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Component.accentColor", accent);
        if (dark) {
            FlatMacDarkLaf.setup();
        } else {
            FlatMacLightLaf.setup();
        }
    }

    private static Color rgb(int c) {
        return new Color(c);
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static final java.util.regex.Pattern GRID_TABLE =
            java.util.regex.Pattern.compile("<table([^>]*?)border=['\"]1['\"]([^>]*)>([\\s\\S]*?)</table>");

    /**
     * Tabellen mit border="1" zeichnet Swing mit grellem Rand. Stattdessen: Tabellenhintergrund
     * in Randfarbe, 1 px Zellabstand und Zellen in Seitenfarbe – das ergibt feine Gitterlinien.
     */
    private static String gridTables(String s) {
        java.util.regex.Matcher m = GRID_TABLE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String attrs = (m.group(1) + m.group(2)).replaceAll("cellspacing=['\"]\\d+['\"]", "");
            String body = m.group(3)
                    .replace("<td", "<td bgcolor='" + hex(bg) + "'")
                    .replace("<th", "<th bgcolor='" + (dark ? "#2b2e33" : "#eef0f4") + "'");
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    "<table" + attrs + " border='0' cellspacing='1' bgcolor='" + hex(border) + "'>" + body + "</table>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Passt HTML an das Design an (Tabellen, im Dark Mode auch die Farben). */
    public static String html(String s) {
        s = gridTables(s);
        if (!dark) {
            return s;
        }
        for (Map.Entry<String, String> e : DARK_MAP.entrySet()) {
            s = s.replace(e.getKey() + "\"", e.getValue() + "\"").replace(e.getKey() + "'", e.getValue() + "'")
                    .replace(e.getKey() + ";", e.getValue() + ";");
        }
        return s;
    }

    public static Font mono(float size) {
        return new Font(MONO_FONT, Font.PLAIN, Math.round(size));
    }
}
