package mjc;

final class Options {
    static final String HELP = """
            Usage: mjc [options] <file>

            Compile a MacroJava program to MIPS assembly.

            Options:
              -o <file>          Write the output to <file> ('-' for standard output)
              --emit=<stage>     Stop after <stage> and write its output:
                                   minijava   MiniJava  (macros expanded)
                                   miniir     MiniIR    (classes and methods lowered)
                                   microir    MicroIR   (expressions flattened)
                                   minira     MiniRA    (registers allocated)
                                   asm        MIPS assembly (default)
              --typecheck-only   Only check the program for errors; write nothing
              --run              Compile, then run the program in SPIM
              --save-temps       Also write every intermediate representation
              -v, --verbose      Show each compiler pass and how long it took
              -h, --help         Show this help and exit
                  --version      Show version information and exit

            Without -o the output is written to <name>.<ext> in the current directory,
            where <name> is the input file name without '.java' (for example Foo.s).
            Use '-' as <file> to read the program from standard input.

            Exit status: 0 on success, 1 for errors in the program, 2 for invalid
            usage, 3 for an internal compiler error. With --run, the exit status of
            SPIM.

            Environment:
              MJC_SPIM           SPIM command used by --run (default: spim, then qtspim)
              MJC_JAVA_OPTS      Extra options for the Java virtual machine
              JAVA_HOME          Java installation used to run mjc

            Examples:
              mjc Factorial.java                      writes Factorial.s
              mjc Factorial.java --run                compiles and runs the program
              mjc Factorial.java --emit=miniir -o -   prints the MiniIR
              mjc Factorial.java --save-temps -v      keeps every stage, shows timings
            """;

    String input;
    String output;
    Stage emit = Stage.ASM;
    boolean typecheckOnly;
    boolean run;
    boolean saveTemps;
    boolean verbose;
    boolean help;
    boolean version;

    private Options() {}

    static Options parse(String[] args) throws MjcException {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> o.help = true;
                case "--version" -> o.version = true;
                case "-v", "--verbose" -> o.verbose = true;
                case "--run" -> o.run = true;
                case "--save-temps" -> o.saveTemps = true;
                case "--typecheck-only", "-fsyntax-only" -> o.typecheckOnly = true;
                case "-o" -> o.output = value(args, ++i, a);
                case "--emit" -> o.emit = Stage.byName(value(args, ++i, a));
                case "-" -> o.setInput(a);
                default -> {
                    if (a.startsWith("--emit=")) {
                        o.emit = Stage.byName(a.substring("--emit=".length()));
                    } else if (a.startsWith("-o") && a.length() > 2) {
                        o.output = a.substring(2);
                    } else if (a.startsWith("-")) {
                        throw MjcException.usage("unrecognized option '" + a + "'");
                    } else {
                        o.setInput(a);
                    }
                }
            }
        }

        if (o.help || o.version) {
            return o;
        }
        if (o.input == null) {
            throw MjcException.usage("no input file");
        }
        if (o.run && o.emit != Stage.ASM) {
            throw MjcException.usage("--run cannot be combined with --emit=" + o.emit.cliName);
        }
        if (o.run && "-".equals(o.output)) {
            throw MjcException.usage("--run cannot be combined with '-o -' (the program's output goes there)");
        }
        if (o.run && o.typecheckOnly) {
            throw MjcException.usage("--run cannot be combined with --typecheck-only");
        }
        if (o.typecheckOnly && o.emit != Stage.ASM) {
            throw MjcException.usage("--typecheck-only cannot be combined with --emit");
        }
        return o;
    }

    private void setInput(String file) throws MjcException {
        if (input != null) {
            throw MjcException.usage("only one input file is supported (got '" + input + "' and '" + file + "')");
        }
        input = file;
    }

    private static String value(String[] args, int i, String option) throws MjcException {
        if (i >= args.length) {
            throw MjcException.usage("option '" + option + "' requires an argument");
        }
        return args[i];
    }
}
