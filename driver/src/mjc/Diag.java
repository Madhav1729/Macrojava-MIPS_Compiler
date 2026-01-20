package mjc;

final class Diag {
    static boolean verbose;

    private static final boolean COLOR =
            "1".equals(System.getProperty("mjc.color")) && System.getenv("NO_COLOR") == null;

    private Diag() {}

    static void error(String file, String message) {
        String where = file != null ? style("1", file + ":") : "mjc:";
        System.err.println(where + " " + style("1;31", "error:") + " " + message);
    }

    static void note(String message) {
        System.err.println("  " + style("1;36", "note:") + " " + message);
    }

    static void step(String what, long millis) {
        if (verbose) {
            System.err.printf("%s %-44s %6d ms%n", style("2", "[mjc]"), what, millis);
        }
    }

    static void info(String message) {
        if (verbose) {
            System.err.println(style("2", "[mjc]") + " " + message);
        }
    }

    private static String style(String sgr, String text) {
        return COLOR ? "\u001b[" + sgr + "m" + text + "\u001b[0m" : text;
    }
}
