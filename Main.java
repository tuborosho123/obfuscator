import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;


public class Main {

    // kw — слова Java, их нельзя превращать в a, b, c
    static final Set<String> kw = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "yield", "sealed", "permits"));

    // jl — классы java.lang, они пишутся без import, поэтому тоже нельзя переименовывать
    static final Set<String> jl = new HashSet<>(Arrays.asList(
            "String", "System", "Object", "Class", "Integer", "Long", "Double", "Float", "Boolean",
            "Character", "Byte", "Short", "Number", "Math", "StrictMath", "StringBuilder", "StringBuffer",
            "CharSequence", "Comparable", "Iterable", "Readable", "Runnable", "Thread", "ThreadLocal",
            "InheritableThreadLocal", "ThreadGroup", "Runtime", "Process", "ProcessBuilder", "Enum",
            "Record", "Void", "Package", "Module", "ClassLoader", "Compiler", "SecurityManager",
            "Throwable", "Exception", "Error", "RuntimeException", "InterruptedException",
            "ClassNotFoundException", "IllegalArgumentException", "IllegalStateException",
            "NullPointerException", "NumberFormatException", "ArithmeticException",
            "UnsupportedOperationException", "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
            "StringIndexOutOfBoundsException", "SecurityException", "CloneNotSupportedException",
            "ReflectiveOperationException", "NoSuchMethodException", "NoSuchFieldException",
            "IllegalAccessException", "InstantiationException", "AssertionError", "OutOfMemoryError",
            "StackOverflowError", "InternalError", "UnknownError", "VirtualMachineError",
            "StackTraceElement", "AutoCloseable", "Appendable", "Override", "Deprecated",
            "SuppressWarnings", "FunctionalInterface", "SafeVarargs"));

    // ops — операторы из нескольких символов. Длинные стоят первыми, чтобы >>= не разрезался на >
    static final String[] ops = {
            ">>>=", "<<=", ">>=", ">>>", "...", "->", "::", "==", "!=", "<=", ">=",
            "&&", "||", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>"};

    // тип кусочка кода: слово, число, строка, символ в кавычках, оператор
    enum Kind { WORD, NUM, STR, CHR, OP }

    // один кусочек кода: k — какой он, s — его текст как в файле
    static class Tok {
        final Kind k;
        final String s;

        Tok(Kind k, String s) {
            this.k = k;
            this.s = s;
        }
    }

    /**
     * Режет текст s на токены.
     * Комментарии // и /* *\/ выбрасывает.
     * Строки "..." и символы '...' оставляет целиком, вместе с пробелами внутри.
     */
    static List<Tok> lex(String s) {
        List<Tok> t = new ArrayList<>();
        int i = 0;          // где сейчас стоим в тексте
        int n = s.length(); // длина текста
        while (i < n) {
            char c = s.charAt(i);

            // пробел, таб, перевод строки — просто пропускаем
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // // до конца строки — комментарий, не токен
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }

            // /* ... */ — тоже комментарий, в том числе javadoc
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                continue;
            }

            // строка "..." или текстовый блок """..."""
            if (c == '"') {
                int j;
                if (s.startsWith("\"\"\"", i)) {
                    j = i + 3;
                    while (j < n && !s.startsWith("\"\"\"", j)) j++;
                    j = Math.min(n, j + 3);
                } else {
                    j = i + 1;
                    while (j < n) {
                        char d = s.charAt(j);
                        if (d == '\\') { // \" не конец строки
                            j += 2;
                            continue;
                        }
                        if (d == '"') {
                            j++;
                            break;
                        }
                        j++;
                    }
                }
                t.add(new Tok(Kind.STR, s.substring(i, Math.min(j, n))));
                i = Math.min(j, n);
                continue;
            }

            // символ в одинарных кавычках: 'a', '\n'
            if (c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == '\'') {
                        j++;
                        break;
                    }
                    j++;
                }
                t.add(new Tok(Kind.CHR, s.substring(i, Math.min(j, n))));
                i = Math.min(j, n);
                continue;
            }

            // слово: буква, потом буквы/цифры/_/$  — имя или ключевое слово
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                t.add(new Tok(Kind.WORD, s.substring(i, j)));
                i = j;
                continue;
            }

            // число: 8192, 10_000, 0xFF, 1.5e-3
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (Character.isLetterOrDigit(d) || d == '_') j++;
                    else if (d == '.' && j + 1 < n && Character.isDigit(s.charAt(j + 1))) j++;
                    else if ((d == '+' || d == '-') && j > i && "eEpP".indexOf(s.charAt(j - 1)) >= 0) j++;
                    else break;
                }
                t.add(new Tok(Kind.NUM, s.substring(i, j)));
                i = j;
                continue;
            }

            // оператор: сначала ищем длинный (>=, ==), иначе берём один символ
            String o = null;
            for (String z : ops) {
                if (s.startsWith(z, i)) {
                    o = z;
                    break;
                }
            }
            if (o == null) o = String.valueOf(c);
            t.add(new Tok(Kind.OP, o));
            i += o.length();
        }
        return t;
    }

    /**
     * Собирает простые имена из import.
     * import java.io.File; -> в набор попадёт File. Его переименовывать нельзя.
     */
    static Set<String> imports(List<Tok> t) {
        Set<String> r = new HashSet<>();
        boolean on = false;   // сейчас внутри import/package до точки с запятой
        boolean im = false;   // это именно import, не package
        List<String> w = new ArrayList<>(); // слова до ;
        for (Tok x : t) {
            if (!on && x.k == Kind.WORD && (x.s.equals("import") || x.s.equals("package"))) {
                on = true;
                im = x.s.equals("import");
                w.clear();
                continue;
            }
            if (on) {
                if (x.s.equals(";")) {
                    // последнее слово перед ; — короткое имя класса
                    if (im && !w.isEmpty()) r.add(w.get(w.size() - 1));
                    on = false;
                } else if (x.k == Kind.WORD) {
                    w.add(x.s);
                }
            }
        }
        return r;
    }

    /**
     * m[k] = true, если токен k — своё имя и его можно заменить.
     * Не помечаем: ключевые слова, java.lang, import, куски после точки (System.out) и аннотации @Override.
     */
    static boolean[] marks(List<Tok> t, Set<String> r) {
        boolean[] m = new boolean[t.size()];
        boolean on = false;
        for (int k = 0; k < t.size(); k++) {
            Tok x = t.get(k);
            if (!on && x.k == Kind.WORD && (x.s.equals("import") || x.s.equals("package"))) {
                on = true;
                continue;
            }
            if (on) {
                if (x.s.equals(";")) on = false;
                continue;
            }
            if (x.k != Kind.WORD) continue;
            if (kw.contains(x.s) || jl.contains(x.s) || r.contains(x.s)) continue;

            String a = k > 0 ? t.get(k - 1).s : "";          // что стоит слева
            String b = k + 1 < t.size() ? t.get(k + 1).s : ""; // что стоит справа
            if (a.equals(".") || a.equals("@") || b.equals(".")) continue;
            m[k] = true;
        }
        return m;
    }

    /**
     * Делает список новых имён.
     * Если имён <= 52 — одна буква (a..z, A..Z).
     * Если больше — две буквы (aa, ab, ...).
     * bad — имена, которые занимать нельзя.
     */
    static List<String> pool(int n, Set<String> bad) {
        int z = n <= 52 ? 1 : 2; // длина нового имени
        while (true) {
            String ab = z == 1
                    ? "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    : "abcdefghijklmnopqrstuvwxyz";
            List<String> p = new ArrayList<>();
            fill("", z, ab, bad, p, n);
            if (p.size() >= n) return p;
            z++; // букв не хватило — берём имена длиннее
        }
    }

    // перебор всех слов длины z из алфавита ab, пока не наберём n штук
    static void fill(String pref, int z, String ab, Set<String> bad, List<String> p, int n) {
        if (p.size() >= n) return;
        if (pref.length() == z) {
            if (!bad.contains(pref)) p.add(pref);
            return;
        }
        for (int i = 0; i < ab.length(); i++) {
            fill(pref + ab.charAt(i), z, ab, bad, p, n);
        }
    }

    /**
     * Имя класса, из которого делается имя файла.
     * Если есть public class — берём его, иначе первый class/interface/enum.
     */
    static String fileClass(List<Tok> t) {
        String a = null; // первый класс
        String b = null; // public-класс
        for (int k = 0; k + 1 < t.size(); k++) {
            Tok x = t.get(k);
            if (x.k != Kind.WORD) continue;
            if (!(x.s.equals("class") || x.s.equals("interface") || x.s.equals("enum"))) continue;
            if (k > 0 && t.get(k - 1).s.equals(".")) continue; // это String.class, не объявление
            if (t.get(k + 1).k != Kind.WORD) continue;
            String name = t.get(k + 1).s;
            if (a == null) a = name;
            // смотрим несколько токенов назад: есть ли public
            for (int j = k - 1; j >= 0 && k - j < 8; j--) {
                String y = t.get(j).s;
                if (y.equals(";") || y.equals("{") || y.equals("}")) break;
                if (y.equals("public")) {
                    b = name;
                    break;
                }
            }
        }
        return b != null ? b : a;
    }

    // буква, цифра, _ или $ — между двумя такими при склейке нужен пробел
    static boolean word(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    // если склеить операторы a и b, получится // или /* или другой оператор — нужен пробел
    static boolean danger(String a, String b) {
        String s = a + b;
        if (s.equals("//") || s.equals("/*")) return true;
        for (String o : ops) {
            if (o.startsWith(s)) return true;
        }
        return false;
    }

    /**
     * Склеивает токены в одну строку.
     * m — таблица старое имя -> новое.
     * Пробел ставим только если без него слипнутся два слова или два оператора.
     */
    static String render(List<Tok> t, Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        String prev = "";
        Kind pk = null;     // тип предыдущего куска
        boolean on = false; // внутри import/package имена не меняем
        for (int k = 0; k < t.size(); k++) {
            Tok x = t.get(k);
            if (!on && x.k == Kind.WORD && (x.s.equals("import") || x.s.equals("package"))) {
                on = true;
            }
            // меняем имя, если оно есть в таблице и это не a.b и не @Имя
            boolean rn = !on && x.k == Kind.WORD && m.containsKey(x.s)
                    && !prev.equals(".") && !prev.equals("@");
            String s = rn ? m.get(x.s) : x.s;
            if (on && x.s.equals(";")) on = false;

            if (sb.length() > 0) {
                char u = sb.charAt(sb.length() - 1);
                char v = s.charAt(0);
                boolean sp = (word(u) && word(v))
                        || (pk == Kind.OP && x.k == Kind.OP && danger(prev, s));
                if (sp) sb.append(' ');
            }
            sb.append(s);
            prev = s;
            pk = x.k;
        }
        return sb.toString();
    }

    /**
     * Один файл: прочитать -> разобрать -> переименовать -> сжать -> записать рядом.
     */
    static void go(Path f) throws IOException {
        String s = Files.readString(f, StandardCharsets.UTF_8);
        // BOM в начале файла ломает первый токен — срезаем
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);

        List<Tok> t = lex(s);
        Set<String> r = imports(t);
        boolean[] mark = marks(t, r);

        // id — свои имена в порядке первого появления
        LinkedHashSet<String> id = new LinkedHashSet<>();
        for (int k = 0; k < t.size(); k++) {
            if (mark[k]) id.add(t.get(k).s);
        }

        // main не переименовываем: с него JVM запускает программу
        List<String> rn = new ArrayList<>();
        for (String name : id) {
            if (!name.equals("main")) rn.add(name);
        }

        // bad — всё, что нельзя выдать как новое имя
        Set<String> bad = new HashSet<>(kw);
        bad.addAll(jl);
        bad.addAll(r);
        bad.add("main");
        for (int k = 0; k < t.size(); k++) {
            if (t.get(k).k == Kind.WORD && !mark[k]) bad.add(t.get(k).s);
        }

        List<String> p = pool(rn.size(), bad);
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < rn.size(); i++) m.put(rn.get(i), p.get(i));

        String out = render(t, m);

        // имя нового файла = новое имя класса
        String cn = fileClass(t);
        String nn = cn == null
                ? f.getFileName().toString().replace(".java", "")
                : m.getOrDefault(cn, cn);

        // пишем в ту же папку, чтобы package совпал с путём
        Path dir = f.toAbsolutePath().getParent();
        Path to = dir.resolve(nn + ".java");
        // если имя не изменилось — не затираем исходник
        if (to.equals(f.toAbsolutePath())) {
            to = dir.resolve(nn + "_obf.java");
        }
        Files.writeString(to, out, StandardCharsets.UTF_8);

        System.out.println("Файл: " + f.getFileName());
        System.out.println("Найдено идентификаторов: " + id.size() + ", переименовано: " + rn.size() + " (main сохранён)");
        String mode;
        if (rn.isEmpty()) mode = "нечего переименовывать";
        else if (p.get(0).length() == 1) mode = "односимвольные (<= 52)";
        else mode = "двухсимвольные (> 52)";
        System.out.println("Новые имена: " + mode);
        if (cn != null) {
            System.out.println("Класс/файл: " + cn + " -> " + nn + " (" + nn + ".java)");
        }
        System.out.println("Таблица переименований:");
        for (Map.Entry<String, String> e : m.entrySet()) {
            System.out.printf("  %-20s -> %s%n", e.getKey(), e.getValue());
        }
        int pc = s.isEmpty() ? 0 : (int) (100 - out.length() * 100L / s.length());
        System.out.printf("Размер: %d -> %d символов (-%d%%), строк: %d -> 1%n",
                s.length(), out.length(), pc, s.lines().count());
        System.out.println("Результат: " + to);
    }

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        String s;
        if (args.length > 0) {
            s = args[0]; // путь можно передать аргументом
        } else {
            System.out.print("Введите путь к директории с .java файлами: ");
            s = sc.nextLine();
        }

        // кавычки снимаем: проводник копирует путь как "D:\папка"
        Path d = Paths.get(s.trim().replace("\"", ""));
        if (!Files.isDirectory(d)) {
            System.out.println("Это не директория: " + d);
            return;
        }

        List<Path> a = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(d, "*.java")) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) a.add(p);
            }
        }
        a.sort(null);
        if (a.isEmpty()) {
            System.out.println("В директории нет .java файлов.");
            return;
        }
        for (Path f : a) go(f);

        System.out.println("Готово. Результат лежит рядом с исходником (старый файл можно удалить вручную).");
    }
}
