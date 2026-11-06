package trainer.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Übersetzt typische javac-Meldungen in eine kurze Erklärung auf Deutsch. */
public final class ErrorHelp {

    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    static {
        rule("';' expected", "Hier fehlt ein Semikolon. Jede Anweisung endet mit ; (meist am Ende der Zeile davor).");
        rule("'\\)' expected", "Eine runde Klammer ist nicht geschlossen. Zähle die ( und ) in dieser Zeile.");
        rule("'\\(' expected", "Hier erwartet Java eine öffnende runde Klammer, z. B. nach einem Methodennamen oder nach if/for/while.");
        rule("'\\{' expected", "Hier fehlt eine öffnende geschweifte Klammer {, z. B. nach dem Klassenkopf oder der Methode.");
        rule("reached end of file while parsing", "Die Datei endet, bevor alle { geschlossen sind. Meist fehlt ganz unten eine }.");
        rule("class, interface, enum, or record expected", "Code steht außerhalb einer Klasse. Oft ist eine } zu viel, sodass die Klasse zu früh endet.");
        rule("unclosed string literal", "Ein Text in Anführungszeichen ist nicht geschlossen. Jeder String braucht \" am Anfang und am Ende.");
        rule("not a statement", "Das ist keine vollständige Anweisung. Fehlt ein Methodenaufruf, eine Zuweisung oder ein Operator?");
        rule("illegal start of expression", "Java hat hier etwas Unerwartetes gefunden. Häufig: Methode in einer Methode, fehlende } davor oder ein Tippfehler.");
        rule("illegal start of type", "Hier steht Code an einer Stelle, wo nur Attribute oder Methoden stehen dürfen. Gehört er in eine Methode?");
        rule("<identifier> expected", "Java erwartet hier einen Namen. Häufig steht eine Anweisung direkt in der Klasse statt in einer Methode.");
        rule("cannot find symbol[\\s\\S]*symbol:\\s+class", "Diese Klasse kennt Java nicht. Tippfehler? Groß/klein beachten. Fehlt ein import (z. B. javax.swing.*)?");
        rule("cannot find symbol[\\s\\S]*symbol:\\s+method", "Diese Methode gibt es so nicht. Prüfe Schreibweise, Groß/klein und die Parameter (Anzahl und Typen).");
        rule("cannot find symbol[\\s\\S]*symbol:\\s+variable", "Diese Variable ist hier unbekannt. Tippfehler, oder wurde sie in einem anderen Block (zwischen { }) angelegt?");
        rule("cannot find symbol", "Java kennt diesen Namen nicht. Prüfe Schreibweise, Groß/klein und ob alles deklariert bzw. importiert ist.");
        rule("incompatible types: String cannot be converted to int", "Ein Text ist keine Zahl. Wandle ihn mit Integer.parseInt(text) um.");
        rule("incompatible types: String cannot be converted to double", "Ein Text ist keine Zahl. Wandle ihn mit Double.parseDouble(text) um.");
        rule("incompatible types: possible lossy conversion from double to int", "Ein double passt nicht ohne Verlust in ein int. Caste ausdrücklich: (int) wert.");
        rule("incompatible types: int cannot be converted to String", "Eine Zahl ist kein String. Nutze String.valueOf(zahl) oder \"\" + zahl.");
        rule("incompatible types", "Die Typen passen nicht zusammen. Links und rechts vom = (oder beim Parameter) muss derselbe Typ stehen.");
        rule("missing return statement", "Die Methode verspricht einen Rückgabewert, aber nicht jeder Weg endet mit return.");
        rule("unreachable statement", "Diese Zeile wird nie erreicht, weil davor schon return, break oder throw steht.");
        rule("might not have been initialized", "Die Variable hat noch keinen Wert. Gib ihr beim Anlegen einen Startwert.");
        rule("is already defined", "Diesen Namen gibt es hier schon. Variablen und Methoden brauchen eindeutige Namen.");
        rule("non-static method .* cannot be referenced from a static context", "Aus main (static) kannst du keine Objektmethode direkt aufrufen. Erzeuge erst ein Objekt mit new.");
        rule("non-static variable .* cannot be referenced from a static context", "Aus main (static) kommst du nicht direkt an Attribute. Erzeuge erst ein Objekt mit new.");
        rule("is public, should be declared in a file named", "Eine public class muss genauso heißen wie ihre Datei. Benenne die Klasse oder die Datei um.");
        rule("does not override abstract method", "Die Klasse implementiert ein Interface (oder erbt abstrakt), aber eine Pflichtmethode fehlt. Schreib sie mit @Override dazu.");
        rule("method does not override or implement a method from a supertype", "@Override steht über einer Methode, die es in der Oberklasse nicht gibt. Tippfehler im Namen oder falsche Parameter?");
        rule("unreported exception", "Diese Methode kann eine geprüfte Exception werfen. Fang sie mit try/catch oder ergänze throws.");
        rule("bad operand types for binary operator", "Dieser Operator passt nicht zu den Typen, z. B. && mit int oder + mit boolean.");
        rule("cannot be applied to given types", "Die Methode oder der Konstruktor wird mit falschen Parametern aufgerufen. Vergleiche Anzahl und Typen.");
        rule("no suitable constructor found", "Es gibt keinen Konstruktor mit diesen Parametern. Vergleiche mit der Klasse oder der JDK-Doku.");
        rule("no suitable method found", "Es gibt keine Methode mit diesen Parametern. Vergleiche Anzahl und Typen.");
        rule("has private access", "Das ist private und von außen nicht erreichbar. Nutze einen Getter oder Setter.");
        rule("is abstract; cannot be instantiated", "Von einer abstrakten Klasse oder einem Interface kann man kein Objekt mit new erzeugen.");
        rule("char cannot be dereferenced|int cannot be dereferenced|double cannot be dereferenced|boolean cannot be dereferenced",
                "Primitive Typen (int, double, char, boolean) haben keine Methoden. Ein Punkt dahinter geht nicht.");
        rule("else' without 'if", "Vor dem else fehlt das passende if, oft wegen eines ; direkt nach if (...) oder einer fehlenden }.");
        rule("orphaned case|orphaned default", "case und default dürfen nur innerhalb von switch (...) { } stehen.");
        rule("deprecat", "Das funktioniert, ist aber veraltet. In der Klausur ist das in Ordnung.");
    }

    private ErrorHelp() {
    }

    private static void rule(String regex, String text) {
        RULES.put(Pattern.compile(regex), text);
    }

    public static String explain(String message) {
        for (Map.Entry<Pattern, String> e : RULES.entrySet()) {
            Matcher m = e.getKey().matcher(message);
            if (m.find()) {
                return e.getValue();
            }
        }
        return "";
    }
}
