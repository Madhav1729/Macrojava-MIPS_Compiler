package mjc;

public final class Main {
    static final String VERSION = "1.0.0";

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (MjcException e) {
            Diag.error(null, e.getMessage());
            System.err.println("Try 'mjc --help' for more information.");
            return e.exitCode;
        }

        if (opt.help) {
            System.out.print(Options.HELP);
            return 0;
        }
        if (opt.version) {
            System.out.println("mjc " + VERSION + " - MacroJava to MIPS compiler");
            return 0;
        }

        Diag.verbose = opt.verbose;
        try {
            return new Compiler(opt, Toolchain.locate()).compile();
        } catch (MjcException e) {
            Diag.error(e.file, e.getMessage());
            for (String note : e.notes) {
                Diag.note(note);
            }
            if (opt.verbose && e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            return e.exitCode;
        }
    }
}
