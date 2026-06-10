package mjc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

final class Compiler {
    private static final List<Stage> JAVA_STAGES = List.of(Stage.MINIIR, Stage.MICROIR, Stage.MINIRA, Stage.ASM);

    private static final Pattern PASS_FAILURE = Pattern.compile(
            "^(?:ParseException|TokenMgrError|(?:[a-z_][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error))(?::\\s.*)?$");

    private static final String TYPECHECK_OK = "Program type checked successfully";

    private final Options opt;
    private final Toolchain tc;
    private final String displayName;
    private final String baseName;

    Compiler(Options opt, Toolchain tc) {
        this.opt = opt;
        this.tc = tc;
        if (opt.input.equals("-")) {
            displayName = "<stdin>";
            baseName = "a";
        } else {
            displayName = opt.input;
            String name = Path.of(opt.input).getFileName().toString();
            int dot = name.lastIndexOf('.');
            baseName = dot > 0 ? name.substring(0, dot) : name;
        }
    }

    int compile() throws MjcException {
        String source = readSource();

        String minijava = timed(Stage.MINIJAVA.description, () -> preprocess(source));
        if (opt.emit == Stage.MINIJAVA) {
            writeResult(Stage.MINIJAVA, minijava);
            return 0;
        }
        saveTemp(Stage.MINIJAVA, minijava);

        timed("type checking     (MiniJava)", () -> {
            typecheck(minijava);
            return null;
        });
        if (opt.warnUninitialized) {
            timed("uninitialized-variable check", () -> {
                warnUninitialized(minijava);
                return null;
            });
        }
        if (opt.typecheckOnly) {
            return 0;
        }

        String program = minijava;
        if (opt.optLevel >= 1) {
            program = timed(Stage.TAC.description, () -> runPass(Stage.TAC, minijava));
            if (opt.emit == Stage.TAC) {
                writeResult(Stage.TAC, program);
                return 0;
            }
            saveTemp(Stage.TAC, program);
            program = optimize(program);
            if (opt.emit == Stage.OPT) {
                writeResult(Stage.OPT, program);
                return 0;
            }
            saveTemp(Stage.OPT, program);
        }
        for (Stage stage : JAVA_STAGES) {
            String input = program;
            program = timed(stage.description, () -> runPass(stage, input));
            if (stage == opt.emit) {
                break;
            }
            saveTemp(stage, program);
        }

        if (!opt.run) {
            writeResult(opt.emit, program);
            return 0;
        }
        return compileAndRun(program);
    }


    private String preprocess(String source) throws MjcException {
        PassRunner.Result r;
        try {
            r = PassRunner.exec(List.of(tc.preprocessor().toString()), source);
        } catch (IOException e) {
            throw MjcException.environment("cannot run the macro expander " + tc.preprocessor() + ": " + e.getMessage(), e);
        }
        if (r.exitCode() != 0) {
            throw MjcException.compile(displayName, "syntax error: this is not a valid MacroJava program")
                    .note("common causes: a missing ';' or brace, an undefined macro,"
                            + " or a macro used with the wrong number of arguments");
        }
        return r.stdout();
    }

    private void typecheck(String minijava) throws MjcException {
        PassRunner.Result r;
        try {
            // Separate JVM because P2 calls System.exit on errors.
            r = PassRunner.runInChildJvm(tc.java(), tc.typeChecker(), "P2", minijava);
        } catch (IOException e) {
            throw MjcException.environment("cannot run the type checker: " + e.getMessage(), e);
        }
        String out = r.stdout().strip();

        if (r.exitCode() == 0 && out.equals(TYPECHECK_OK)) {
            return;
        }
        if (out.equals("Type error")) {
            throw MjcException.compile(displayName, "type error");
        }
        if (out.equals("Symbol not found")) {
            MjcException e = MjcException.compile(displayName,
                    "symbol not found (a class, method or variable is used but never declared)");
            if (minijava.contains("Function<") && !minijava.contains("import java.util.function.Function")) {
                e.note("lambdas need 'import java.util.function.Function;' at the top of the file");
            }
            throw e;
        }
        if (out.startsWith("ParseException")) {
            String message = firstLine(out).replaceFirst("^ParseException:\\s*", "");
            throw MjcException.compile(displayName, "syntax error: " + message)
                    .note("line and column refer to the program after macro expansion;"
                            + " view it with --emit=minijava");
        }
        throw MjcException.internal(displayName,
                "the type checker exited with status " + r.exitCode() + ": " + firstLine(out), null);
    }

    // -O1: P9; -O2: P9, P10, P9. Then P11.
    private String optimize(String tac) throws MjcException {
        String program = timed(Stage.OPT.description, () -> runPass(Stage.OPT, tac));
        if (opt.optLevel >= 2) {
            String before = program;
            String inlined = timed("inlining          (TAC)", () -> runPass(tc.inliner(), "P10",
                    "inlining", before, "--inline-limit=" + opt.inlineLimit));
            program = timed(Stage.OPT.description, () -> runPass(Stage.OPT, inlined));
        }
        String folded = program;
        return timed("temp folding      (TAC -> MiniJava)", () -> runPass(tc.refolder(), "P11",
                "temp folding", folded));
    }

    private void warnUninitialized(String minijava) throws MjcException {
        String out = runPass(tc.uninitChecker(), "P7", "uninitialized-variable check", minijava, new String[0], true);
        for (String line : out.split("\\R")) {
            if (!line.isBlank()) {
                Diag.warning(displayName, line.strip());
            }
        }
    }

    private String runPass(Stage stage, String input) throws MjcException {
        return runPass(tc.passJar(stage.passJar), stage.passMain, stage.description, input);
    }

    private String runPass(java.nio.file.Path jar, String main, String description, String input, String... args)
            throws MjcException {
        return runPass(jar, main, description, input, args, false);
    }

    private String runPass(java.nio.file.Path jar, String main, String description, String input,
                           String[] args, boolean emptyOk) throws MjcException {
        String passName = main + " (" + description.replaceAll("\\s+", " ") + ")";
        String out;
        try {
            out = PassRunner.runInProcess(jar, main, input, args);
        } catch (Exception e) {
            Throwable cause = e instanceof PassRunner.PassCrash ? e.getCause() : e;
            throw MjcException.internal(displayName, "pass " + passName + " crashed: " + cause, cause);
        }
        String failure = failureIn(out);
        if (failure != null) {
            throw MjcException.internal(displayName, "pass " + passName + " failed: " + failure, null);
        }
        if (out.isBlank() && !emptyOk) {
            throw MjcException.internal(displayName, "pass " + passName + " produced no output", null);
        }
        return out;
    }

    private static String failureIn(String out) {
        if (out.isBlank()) {
            return null;
        }
        String[] lines = out.strip().split("\\R");
        for (String line : new String[] {lines[0], lines[lines.length - 1]}) {
            if (PASS_FAILURE.matcher(line.strip()).matches()) {
                return line.strip();
            }
        }
        return null;
    }


    private int compileAndRun(String asm) throws MjcException {
        boolean keep = opt.output != null || opt.saveTemps;
        Path file;
        try {
            file = keep ? outputPath(Stage.ASM) : Files.createTempFile("mjc-" + baseName + "-", ".s");
        } catch (IOException e) {
            throw MjcException.environment("cannot create a temporary file: " + e.getMessage(), e);
        }
        try {
            write(file, asm);
            return runSpim(file);
        } finally {
            if (!keep) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private int runSpim(Path asm) throws MjcException {
        List<String> cmd = tc.spim();
        cmd.add("-file");
        cmd.add(asm.toString());
        Diag.info("running: " + String.join(" ", cmd));

        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                boolean banner = true;
                String line;
                while ((line = r.readLine()) != null) {
                    if (banner && line.startsWith("Loaded: ")) {
                        continue;
                    }
                    banner = false;
                    System.out.println(line);
                    System.out.flush();
                }
            }
            return p.waitFor();
        } catch (IOException e) {
            throw MjcException.environment("cannot run SPIM (" + cmd.get(0) + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MjcException.environment("interrupted while running SPIM", e);
        }
    }


    private String readSource() throws MjcException {
        try {
            if (opt.input.equals("-")) {
                return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Files.readString(Path.of(opt.input), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw MjcException.environment("cannot read '" + opt.input + "': no such file", e);
        } catch (IOException e) {
            throw MjcException.environment("cannot read '" + opt.input + "': " + e.getMessage(), e);
        }
    }

    private void writeResult(Stage stage, String text) throws MjcException {
        if ("-".equals(opt.output)) {
            System.out.print(text);
            System.out.flush();
            return;
        }
        write(outputPath(stage), text);
    }

    private void saveTemp(Stage stage, String text) throws MjcException {
        if (opt.saveTemps) {
            write(tempDir().resolve(baseName + stage.extension), text);
        }
    }

    private Path outputPath(Stage stage) {
        if (opt.output != null && !opt.output.equals("-")) {
            return Path.of(opt.output);
        }
        return Path.of(baseName + stage.extension);
    }

    private Path tempDir() {
        if (opt.output != null && !opt.output.equals("-")) {
            Path parent = Path.of(opt.output).getParent();
            if (parent != null) {
                return parent;
            }
        }
        return Path.of("");
    }

    private void write(Path file, String text) throws MjcException {
        try {
            if (!opt.input.equals("-") && Files.exists(file)
                    && Files.isSameFile(file, Path.of(opt.input))) {
                throw MjcException.usage("refusing to overwrite the input file '" + opt.input + "'");
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
            Diag.info("wrote " + file);
        } catch (NoSuchFileException e) {
            throw MjcException.environment("cannot write '" + file + "': directory does not exist", e);
        } catch (IOException e) {
            throw MjcException.environment("cannot write '" + file + "': " + e.getMessage(), e);
        }
    }


    @FunctionalInterface
    private interface Step<T> {
        T run() throws MjcException;
    }

    private static <T> T timed(String what, Step<T> step) throws MjcException {
        long start = System.nanoTime();
        T result = step.run();
        Diag.step(what, (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    private static String firstLine(String s) {
        String line = s.strip().split("\\R", 2)[0];
        return line.isEmpty() ? "(no output)" : line;
    }
}
