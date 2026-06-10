package mjc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

final class PassRunner {
    private static final long PASS_STACK_SIZE = 512L * 1024 * 1024;

    static final List<String> CHILD_JVM_OPTIONS =
            List.of("-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-XX:-UsePerfData");

    record Result(int exitCode, String stdout) {}

    private PassRunner() {}

    // A fresh class loader per run: the passes share class names, and their JavaCC
    // parsers are static and can only be constructed once.
    static String runInProcess(Path jar, String mainClass, String input) throws Exception {
        return runInProcess(jar, mainClass, input, new String[0]);
    }

    static synchronized String runInProcess(Path jar, String mainClass, String input, String[] args)
            throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Throwable[] failure = new Throwable[1];

        InputStream savedIn = System.in;
        PrintStream savedOut = System.out;
        try (URLClassLoader loader = new URLClassLoader(
                "mjc-" + mainClass, new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(captured, false, StandardCharsets.UTF_8));

            Thread thread = new Thread(null, () -> {
                try {
                    Method main = loader.loadClass(mainClass).getMethod("main", String[].class);
                    main.invoke(null, (Object) args);
                } catch (InvocationTargetException e) {
                    failure[0] = e.getCause();
                } catch (Throwable e) {
                    failure[0] = e;
                }
            }, "mjc-" + mainClass, PASS_STACK_SIZE);
            thread.setContextClassLoader(loader);
            thread.start();
            thread.join();
        } finally {
            System.out.flush();
            System.setOut(savedOut);
            System.setIn(savedIn);
        }

        if (failure[0] instanceof Exception e) {
            throw e;
        } else if (failure[0] instanceof Error e) {
            throw new PassCrash(e);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    static Result runInChildJvm(Path java, Path jar, String mainClass, String input) throws IOException {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(java.toString());
        cmd.addAll(CHILD_JVM_OPTIONS);
        cmd.addAll(List.of("-cp", jar.toString(), mainClass));
        return exec(cmd, input);
    }

    static Result exec(List<String> command, String input) throws IOException {
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();

        // Write stdin from another thread to avoid a pipe deadlock.
        Thread feeder = new Thread(() -> {
            try (OutputStream in = p.getOutputStream()) {
                in.write(input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }, "mjc-stdin-feeder");
        feeder.start();

        byte[] out;
        try (InputStream stdout = p.getInputStream()) {
            out = stdout.readAllBytes();
        }
        try {
            int code = p.waitFor();
            feeder.join();
            return new Result(code, new String(out, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for " + command.get(0), e);
        }
    }

    static final class PassCrash extends Exception {
        PassCrash(Error cause) {
            super(cause.toString(), cause);
        }
    }
}
