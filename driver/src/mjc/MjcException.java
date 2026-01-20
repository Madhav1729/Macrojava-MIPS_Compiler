package mjc;

import java.util.ArrayList;
import java.util.List;

final class MjcException extends Exception {
    static final int EXIT_COMPILE_ERROR = 1;
    static final int EXIT_USAGE = 2;
    static final int EXIT_INTERNAL = 3;

    final int exitCode;
    final String file;
    final List<String> notes = new ArrayList<>();

    private MjcException(int exitCode, String file, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.file = file;
    }

    static MjcException compile(String file, String message) {
        return new MjcException(EXIT_COMPILE_ERROR, file, message, null);
    }

    static MjcException usage(String message) {
        return new MjcException(EXIT_USAGE, null, message, null);
    }

    static MjcException environment(String message, Throwable cause) {
        return new MjcException(EXIT_COMPILE_ERROR, null, message, cause);
    }

    static MjcException internal(String file, String message, Throwable cause) {
        MjcException e = new MjcException(EXIT_INTERNAL, file, "internal compiler error: " + message, cause);
        e.notes.add("rerun with --verbose --save-temps to see the intermediate programs");
        return e;
    }

    MjcException note(String note) {
        notes.add(note);
        return this;
    }
}
