package trainer.complete;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Berechnet Vorschläge für die Autovervollständigung.
 * Kennt die Klassen aus Swing/AWT/java.util per Reflection, die eigenen Klassen aus den
 * offenen Dateien per einfachem Textvergleich und die Variablen im aktuellen Code.
 */
public final class Completion {

    /** Ein Vorschlag: was angezeigt wird, was eingefügt wird, und wohin der Cursor danach soll. */
    public record Item(String label, String detail, String insert, Kind kind, int rank) { }

    /** Markiert im Einfügetext, wo der Cursor danach stehen soll. */
    public static final char CARET = '\u2038';

    public enum Kind { METHOD, FIELD, VARIABLE, CLASS, KEYWORD, SNIPPET }

    /** Ergebnis: ab welcher Position ersetzt wird (das bereits getippte Wortstück) und die Vorschläge. */
    public record Result(int replaceFrom, String prefix, List<Item> items) { }

    /** Pakete, die über die Auto-Imports bekannt sind (in dieser Reihenfolge gesucht). */
    private static final String[] PACKAGES = {"java.lang", "javax.swing", "java.awt", "java.awt.event", "java.beans",
            "java.util", "javax.swing.table", "javax.swing.border", "javax.swing.event", "java.text", "java.io"};

    private static final String[] KEYWORDS = ("public private protected static final void class extends implements interface "
            + "new return if else for while do switch case default break continue try catch finally throw throws this super "
            + "int double boolean char long float String true false null import package instanceof abstract").split(" ");

    private static final Set<String> NOT_TYPES = Set.of("new", "return", "else", "if", "for", "while", "switch", "catch",
            "synchronized", "do", "try", "throw", "case");

    private static volatile Map<String, String> jdkClasses;
    private static volatile Set<String> preferred = Set.of();

    /** Namen, die in den Musterlösungen vorkommen, werden bei gleichem Rang zuerst vorgeschlagen. */
    public static void setPreferred(Set<String> names) {
        preferred = Set.copyOf(names);
    }
    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();

    private static final Pattern IDENT_BEFORE = Pattern.compile("[A-Za-z_$][\\w$]*$");

    private Completion() {
    }

    /** Lädt die Klassenliste im Hintergrund, damit der erste Vorschlag schnell kommt. */
    public static void warmUp() {
        Thread t = new Thread(Completion::jdkClasses, "autocomplete-index");
        t.setDaemon(true);
        t.start();
    }

    /**
     * @param text   Inhalt der aktuellen Datei
     * @param caret  Cursorposition
     * @param others Inhalte aller Dateien der Aufgabe (für eigene Klassen wie Model oder View)
     */
    public static Result complete(String text, int caret, List<String> others) {
        String before = text.substring(0, caret);
        Matcher m = IDENT_BEFORE.matcher(before);
        String prefix = m.find() ? m.group() : "";
        int start = caret - prefix.length();
        Map<String, UserClass> userClasses = parseUserClasses(others);

        List<Item> items = new ArrayList<>();
        if (start > 0 && before.charAt(start - 1) == '.') {
            String qualifier = qualifierBefore(before, start - 1);
            items.addAll(memberItems(qualifier, text, caret, userClasses));
        } else {
            items.addAll(plainItems(text, caret, userClasses));
        }

        String p = prefix.toLowerCase(Locale.ROOT);
        Map<String, Item> unique = new LinkedHashMap<>();
        for (Item it : items) {
            String name = (it.kind() == Kind.SNIPPET ? it.label() : it.insert().replaceAll("\\(.*", "")).toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && !name.startsWith(p) && !camelMatch(it.label(), prefix)) {
                continue;
            }
            unique.putIfAbsent(it.label() + "|" + it.kind(), it);
        }
        List<Item> out = new ArrayList<>(unique.values());
        out.sort(Comparator.<Item>comparingInt(it -> it.label().startsWith(prefix) ? 0 : 1)
                .thenComparingInt(Item::rank)
                .thenComparingInt(it -> preferred.contains(it.label().replaceAll("\\(.*", "")) ? 0 : 1)
                .thenComparing(it -> it.label().replaceAll("\\(.*", ""), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(it -> it.label().length()));
        if (out.size() > 150) {
            out = new ArrayList<>(out.subList(0, 150));
        }
        return new Result(start, prefix, out);
    }

    /** "gT" passt auf "getText" (Großbuchstaben-Abkürzung wie in IntelliJ). */
    private static boolean camelMatch(String label, String prefix) {
        if (prefix.length() < 2) {
            return false;
        }
        StringBuilder humps = new StringBuilder();
        String name = label.replaceAll("[(\\s].*", "");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i == 0 || Character.isUpperCase(c)) {
                humps.append(Character.toLowerCase(c));
            }
        }
        return humps.toString().startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    /* ------------------------------------------------------------ Ausdruck vor dem Punkt */

    /** Liest den Ausdruck vor dem Punkt, z. B. "model.getTableModel()" oder "view". */
    static String qualifierBefore(String s, int dot) {
        int i = dot - 1;
        int depth = 0;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == ')' || c == ']') {
                depth++;
            } else if (c == '(' || c == '[') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (depth == 0 && !(Character.isJavaIdentifierPart(c) || c == '.')) {
                break;
            }
            i--;
        }
        return s.substring(i + 1, dot).trim();
    }

    /** Löst einen Ausdruck auf: Ergebnis ist ein Typ-Name und ob nur statische Mitglieder passen. */
    private record TypeRef(String name, boolean statics, boolean array) { }

    private static TypeRef resolve(String expr, String text, int caret, Map<String, UserClass> users) {
        List<String> parts = splitChain(expr);
        if (parts.isEmpty()) {
            return null;
        }
        String first = parts.get(0);
        TypeRef cur;
        String enclosing = enclosingClass(text, caret);
        if (first.equals("this")) {
            cur = enclosing == null ? null : new TypeRef(enclosing, false, false);
        } else if (first.equals("super")) {
            UserClass uc = enclosing == null ? null : users.get(enclosing);
            cur = uc == null || uc.superName == null ? null : new TypeRef(uc.superName, false, false);
        } else if (first.endsWith(")")) {
            // Methode der eigenen Klasse, z. B. getContentPane().
            String name = first.substring(0, first.indexOf('('));
            String t = enclosing == null ? null : returnType(enclosing, name, users);
            cur = t == null ? null : typeRef(t);
        } else {
            String declared = declaredType(text, first);
            if (declared == null && enclosing != null) {
                declared = fieldType(enclosing, first, users);
            }
            if (declared != null) {
                cur = typeRef(declared);
            } else if (Character.isUpperCase(first.charAt(0)) && (users.containsKey(first) || findClass(first) != null)) {
                cur = new TypeRef(first, true, false);
            } else {
                cur = null;
            }
        }
        for (int k = 1; k < parts.size() && cur != null; k++) {
            String seg = parts.get(k);
            if (cur.array) {
                return null;
            }
            String t;
            if (seg.endsWith(")")) {
                t = returnType(cur.name, seg.substring(0, seg.indexOf('(')), users);
            } else {
                t = fieldType(cur.name, seg, users);
                if (t == null && cur.statics && Character.isUpperCase(seg.charAt(0))) {
                    t = cur.name + "$" + seg;
                }
            }
            cur = t == null ? null : typeRef(t);
        }
        return cur;
    }

    private static TypeRef typeRef(String t) {
        t = t.replaceAll("<.*>", "").trim();
        boolean array = t.endsWith("[]");
        return new TypeRef(t.replace("[]", "").trim(), false, array);
    }

    /** Zerlegt "a.b(x, y).c" in [a, b(...), c]. */
    private static List<String> splitChain(String expr) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(' || c == '[') {
                if (depth == 0) {
                    cur.append(c == '(' ? "(" : "[");
                }
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
                if (depth == 0) {
                    cur.append(c == ')' ? ")" : "]");
                }
            } else if (depth == 0) {
                if (c == '.') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        // Array-Zugriff wie fields[i] ergibt den Elementtyp: hier vereinfacht ignoriert
        return out.stream().map(s -> s.replace("[]", "")).filter(s -> !s.isEmpty()).toList();
    }

    /** Sucht "Typ name" im Code (Variable, Parameter, Attribut). */
    private static String declaredType(String text, String name) {
        Pattern p = Pattern.compile("\\b([A-Z][\\w$]*(?:\\s*<[^;(){}]*?>)?(?:\\s*\\[\\s*\\])?|int|double|boolean|char|long|float)\\s+"
                + Pattern.quote(name) + "\\s*(?:=|;|,|\\)|:)");
        Matcher m = p.matcher(text);
        String found = null;
        while (m.find()) {
            found = m.group(1);
        }
        return found;
    }

    private static String enclosingClass(String text, int caret) {
        Matcher m = Pattern.compile("\\bclass\\s+(\\w+)").matcher(text.substring(0, Math.min(caret, text.length())));
        String found = null;
        while (m.find()) {
            found = m.group(1);
        }
        if (found == null) {
            m = Pattern.compile("\\bclass\\s+(\\w+)").matcher(text);
            if (m.find()) {
                found = m.group(1);
            }
        }
        return found;
    }

    /* ------------------------------------------------------------ Mitglieder nach dem Punkt */

    private static List<Item> memberItems(String qualifier, String text, int caret, Map<String, UserClass> users) {
        List<Item> items = new ArrayList<>();
        TypeRef t = resolve(qualifier, text, caret, users);
        if (t == null) {
            return items;
        }
        if (t.array) {
            items.add(new Item("length", "int", "length", Kind.FIELD, 0));
            return items;
        }
        addMembers(t.name, t.statics, users, items, 0);
        return items;
    }

    private static void addMembers(String typeName, boolean statics, Map<String, UserClass> users, List<Item> items, int depth) {
        if (depth > 6) {
            return;
        }
        UserClass uc = users.get(typeName);
        if (uc != null) {
            for (UserMember mem : uc.members) {
                if (statics && !mem.isStatic) {
                    continue;
                }
                items.add(mem.toItem(0));
            }
            if (uc.superName != null) {
                addMembers(uc.superName, statics, users, items, depth + 1);
            }
            return;
        }
        Class<?> c = findClass(typeName);
        if (c == null) {
            return;
        }
        for (Method mt : c.getMethods()) {
            if (statics != Modifier.isStatic(mt.getModifiers()) || mt.isAnnotationPresent(Deprecated.class) || mt.isSynthetic()) {
                continue;
            }
            items.add(methodItem(mt, rankOf(mt.getDeclaringClass(), c)));
        }
        for (Field f : c.getFields()) {
            if (statics != Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Deprecated.class)) {
                continue;
            }
            items.add(new Item(f.getName(), simple(f.getType()), f.getName(), Kind.FIELD, statics ? 0 : 1));
        }
        if (statics) {
            for (Class<?> inner : c.getClasses()) {
                items.add(new Item(inner.getSimpleName(), "Klasse", inner.getSimpleName(), Kind.CLASS, 2));
            }
        }
    }

    /** Methoden der Klasse selbst zuerst, Methoden aus Object ganz hinten. */
    private static int rankOf(Class<?> declaring, Class<?> c) {
        if (declaring == Object.class) {
            return 9;
        }
        return declaring == c ? 0 : 1;
    }

    private static Item methodItem(Method mt, int rank) {
        StringBuilder params = new StringBuilder();
        for (Class<?> p : mt.getParameterTypes()) {
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append(simple(p));
        }
        String insert = mt.getParameterCount() > 0 ? mt.getName() + "(" + CARET + ")" : mt.getName() + "()";
        return new Item(mt.getName() + "(" + params + ")", simple(mt.getReturnType()), insert, Kind.METHOD, rank);
    }

    private static String simple(Class<?> c) {
        return c.isArray() ? simple(c.getComponentType()) + "[]" : c.getSimpleName();
    }

    private static String returnType(String typeName, String method, Map<String, UserClass> users) {
        UserClass uc = users.get(typeName);
        if (uc != null) {
            for (UserMember m : uc.members) {
                if (m.isMethod && m.name.equals(method)) {
                    return m.type;
                }
            }
            return uc.superName == null ? null : returnType(uc.superName, method, users);
        }
        Class<?> c = findClass(typeName);
        if (c == null) {
            return null;
        }
        for (Method mt : c.getMethods()) {
            if (mt.getName().equals(method)) {
                return mt.getReturnType().getTypeName();
            }
        }
        return null;
    }

    private static String fieldType(String typeName, String field, Map<String, UserClass> users) {
        UserClass uc = users.get(typeName);
        if (uc != null) {
            for (UserMember m : uc.members) {
                if (!m.isMethod && m.name.equals(field)) {
                    return m.type;
                }
            }
            return uc.superName == null ? null : fieldType(uc.superName, field, users);
        }
        Class<?> c = findClass(typeName);
        if (c == null) {
            return null;
        }
        try {
            return c.getField(field).getType().getTypeName();
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /* ------------------------------------------------------------ Vorschläge ohne Punkt */

    private static List<Item> plainItems(String text, int caret, Map<String, UserClass> users) {
        List<Item> items = new ArrayList<>();
        // Variablen, Parameter und Attribute im Code
        Matcher m = Pattern.compile("\\b([A-Z][\\w$]*(?:\\s*<[^;(){}]*?>)?(?:\\s*\\[\\s*\\])?|int|double|boolean|char|long|float)\\s+([a-z_][\\w$]*)\\s*(?:=|;|,|\\)|:)")
                .matcher(text);
        while (m.find()) {
            if (m.start(2) <= caret && caret <= m.end(2)) {
                continue; // das Wort, das gerade getippt wird
            }
            items.add(new Item(m.group(2), m.group(1).replaceAll("\\s+", ""), m.group(2), Kind.VARIABLE, 0));
        }
        // Methoden der eigenen Klasse und ihrer Oberklasse (z. B. setTitle in einer View extends JFrame)
        String enclosing = enclosingClass(text, caret);
        if (enclosing != null) {
            List<Item> own = new ArrayList<>();
            addMembers(enclosing, false, users, own, 0);
            for (Item it : own) {
                items.add(new Item(it.label(), it.detail(), it.insert(), it.kind(), Math.max(1, it.rank())));
            }
        }
        // Eigene Klassen und bekannte JDK-Klassen
        for (String uc : users.keySet()) {
            items.add(new Item(uc, "eigene Klasse", uc, Kind.CLASS, 1));
        }
        for (Map.Entry<String, String> e : jdkClasses().entrySet()) {
            items.add(new Item(e.getKey(), e.getValue().substring(0, e.getValue().lastIndexOf('.')), e.getKey(), Kind.CLASS, 3));
        }
        for (String k : KEYWORDS) {
            items.add(new Item(k, "Schlüsselwort", k, Kind.KEYWORD, 2));
        }
        items.add(new Item("sout", "System.out.println(…)", "System.out.println(" + CARET + ");", Kind.SNIPPET, 0));
        items.add(new Item("psvm", "public static void main", "public static void main(String[] args) {\n    " + CARET + "\n}", Kind.SNIPPET, 0));
        items.add(new Item("fori", "for-Schleife mit Index", "for (int i = 0; i < " + CARET + "; i++) {\n    \n}", Kind.SNIPPET, 0));
        return items;
    }

    /* ------------------------------------------------------------ Klassen finden */

    static synchronized Class<?> findClass(String simpleOrFull) {
        if (CLASS_CACHE.containsKey(simpleOrFull)) {
            return CLASS_CACHE.get(simpleOrFull);
        }
        Class<?> found = null;
        List<String> candidates = new ArrayList<>();
        if (simpleOrFull.contains(".")) {
            candidates.add(simpleOrFull);
        } else {
            for (String p : PACKAGES) {
                candidates.add(p + "." + simpleOrFull);
            }
        }
        if (simpleOrFull.contains("$")) {
            // verschachtelte Klasse, z. B. Typ "JFrame$X" aus der Ausdrucksauflösung
            String outer = simpleOrFull.substring(0, simpleOrFull.indexOf('$'));
            Class<?> o = findClass(outer);
            if (o != null) {
                candidates.add(0, o.getName() + simpleOrFull.substring(simpleOrFull.indexOf('$')));
            }
        }
        for (String n : candidates) {
            try {
                found = Class.forName(n, false, Completion.class.getClassLoader());
                break;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // nächstes Paket probieren
            }
        }
        if (found == null) {
            switch (simpleOrFull) {
                case "int" -> found = int.class;
                case "double" -> found = double.class;
                case "boolean" -> found = boolean.class;
                default -> { }
            }
            if (found != null && found.isPrimitive()) {
                found = null; // primitive Typen haben keine Mitglieder
            }
        }
        CLASS_CACHE.put(simpleOrFull, found);
        return found;
    }

    /** Alle öffentlichen Klassen der bekannten Pakete: einfacher Name → voller Name. */
    static Map<String, String> jdkClasses() {
        Map<String, String> m = jdkClasses;
        if (m != null) {
            return m;
        }
        Map<String, String> out = new TreeMap<>();
        try {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            Set<String> pkgs = Set.of(PACKAGES);
            Path modules = jrt.getPath("/modules");
            try (Stream<Path> mods = Files.list(modules)) {
                for (Path mod : mods.toList()) {
                    for (String p : pkgs) {
                        Path dir = mod.resolve(p.replace('.', '/'));
                        if (!Files.isDirectory(dir)) {
                            continue;
                        }
                        try (Stream<Path> files = Files.list(dir)) {
                            for (Path f : files.toList()) {
                                String fn = f.getFileName().toString();
                                if (fn.endsWith(".class") && !fn.contains("$") && !fn.equals("module-info.class")
                                        && !fn.equals("package-info.class")) {
                                    String simple = fn.substring(0, fn.length() - 6);
                                    if (isPublic(p + "." + simple)) {
                                        out.putIfAbsent(simple, p + "." + simple);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            // ohne Klassenliste geht es auch, dann eben nur Variablen und Mitglieder
        }
        jdkClasses = out;
        return out;
    }

    private static boolean isPublic(String name) {
        try {
            Class<?> c = Class.forName(name, false, Completion.class.getClassLoader());
            return Modifier.isPublic(c.getModifiers()) && !c.isAnnotationPresent(Deprecated.class);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /* ------------------------------------------------------------ eigene Klassen */

    private static final class UserMember {
        String name;
        String type;
        String params = "";
        boolean isMethod;
        boolean isStatic;

        Item toItem(int rank) {
            if (isMethod) {
                return new Item(name + "(" + params + ")", type, params.isBlank() ? name + "()" : name + "(" + CARET + ")", Kind.METHOD, rank);
            }
            return new Item(name, type, name, Kind.FIELD, rank);
        }
    }

    private static final class UserClass {
        String superName;
        final List<UserMember> members = new ArrayList<>();
    }

    private static final Pattern CLASS_DECL = Pattern.compile("\\b(?:class|interface)\\s+(\\w+)(?:\\s+extends\\s+([\\w.]+))?");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(?m)^[ \\t]*((?:(?:public|private|protected|static|final|abstract|synchronized)[ \\t]+)*)([\\w.<>\\[\\], ]+?)[ \\t]+(\\w+)[ \\t]*\\(([^)]*)\\)\\s*(?:throws[\\w\\s,.]+)?\\{");
    private static final Pattern FIELD_DECL = Pattern.compile(
            "(?m)^[ \\t]*((?:(?:public|private|protected|static|final)[ \\t]+)+)([\\w.<>\\[\\],]+(?:[ \\t]*<[^;\\n]*?>)?)[ \\t]+(\\w+)[ \\t]*(?:=|;)");

    private static Map<String, UserClass> parseUserClasses(List<String> files) {
        Map<String, UserClass> out = new HashMap<>();
        for (String f : files) {
            Matcher cm = CLASS_DECL.matcher(f);
            if (!cm.find()) {
                continue;
            }
            UserClass uc = new UserClass();
            uc.superName = cm.group(2);
            Matcher mm = METHOD_DECL.matcher(f);
            while (mm.find()) {
                String type = mm.group(2).trim();
                if (NOT_TYPES.contains(type) || NOT_TYPES.contains(mm.group(3)) || type.contains("=")) {
                    continue;
                }
                UserMember m = new UserMember();
                m.isMethod = true;
                m.name = mm.group(3);
                m.type = type.replaceAll("^.*\\s", "");
                m.isStatic = mm.group(1).contains("static");
                m.params = mm.group(4).replaceAll("\\s+", " ").trim();
                if (!m.name.equals(cm.group(1))) {
                    uc.members.add(m);
                }
            }
            Matcher fm = FIELD_DECL.matcher(f);
            while (fm.find()) {
                UserMember m = new UserMember();
                m.name = fm.group(3);
                m.type = fm.group(2).trim();
                m.isStatic = fm.group(1).contains("static");
                uc.members.add(m);
            }
            out.put(cm.group(1), uc);
        }
        return out;
    }
}
