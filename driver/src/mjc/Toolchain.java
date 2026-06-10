package mjc;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Toolchain {
    final Path home;

    private Toolchain(Path home) {
        this.home = home;
    }

    static Toolchain locate() throws MjcException {
        Path home;
        String prop = System.getProperty("mjc.home", System.getenv("MJC_HOME"));
        if (prop != null && !prop.isEmpty()) {
            home = Path.of(prop);
        } else {
            try {
                Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                home = jar.getParent().getParent().getParent();
            } catch (URISyntaxException | NullPointerException e) {
                throw MjcException.environment("cannot determine the mjc installation directory; set MJC_HOME", e);
            }
        }
        Toolchain tc = new Toolchain(home.toAbsolutePath().normalize());
        tc.require(tc.preprocessor());
        tc.require(tc.typeChecker());
        tc.require(tc.uninitChecker());
        tc.require(tc.inliner());
        tc.require(tc.refolder());
        for (Stage s : Stage.values()) {
            if (s.passJar != null) {
                tc.require(tc.passJar(s.passJar));
            }
        }
        return tc;
    }

    Path preprocessor() {
        return home.resolve("libexec/mjc/mjc-pp");
    }

    Path typeChecker() {
        return passJar("p2.jar");
    }

    Path uninitChecker() {
        return passJar("p7.jar");
    }

    Path inliner() {
        return passJar("p10.jar");
    }

    Path refolder() {
        return passJar("p11.jar");
    }

    Path passJar(String name) {
        return home.resolve("lib/mjc/passes").resolve(name);
    }

    Path java() {
        return Path.of(System.getProperty("java.home"), "bin", "java");
    }

    List<String> spim() throws MjcException {
        String custom = System.getenv("MJC_SPIM");
        if (custom != null && !custom.isBlank()) {
            return new ArrayList<>(Arrays.asList(custom.trim().split("\\s+")));
        }
        for (String candidate : List.of("spim", "qtspim")) {
            if (onPath(candidate)) {
                return new ArrayList<>(List.of(candidate));
            }
        }
        throw MjcException.environment("--run needs SPIM, but neither 'spim' nor 'qtspim' is in PATH", null)
                .note("install SPIM, or point MJC_SPIM at it");
    }

    private void require(Path p) throws MjcException {
        if (!Files.exists(p)) {
            throw MjcException.environment("broken mjc installation: missing " + p, null)
                    .note("build the compiler with 'make' (or set MJC_HOME to an installation)");
        }
    }

    private static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, program))) {
                return true;
            }
        }
        return false;
    }
}
