package mjc;

import java.util.Arrays;
import java.util.stream.Collectors;

enum Stage {
    MINIJAVA("minijava", ".minijava", null, null, "macro expansion   (MacroJava -> MiniJava)"),
    MINIIR("miniir", ".miniir", "p3.jar", "P3", "lowering          (MiniJava  -> MiniIR)"),
    MICROIR("microir", ".microir", "p4.jar", "P4", "flattening        (MiniIR    -> MicroIR)"),
    MINIRA("minira", ".minira", "p5.jar", "P5", "register alloc    (MicroIR   -> MiniRA)"),
    ASM("asm", ".s", "p6.jar", "P6", "code generation   (MiniRA    -> MIPS)");

    final String cliName;
    final String extension;
    final String passJar;
    final String passMain;
    final String description;

    Stage(String cliName, String extension, String passJar, String passMain, String description) {
        this.cliName = cliName;
        this.extension = extension;
        this.passJar = passJar;
        this.passMain = passMain;
        this.description = description;
    }

    static Stage byName(String name) throws MjcException {
        for (Stage s : values()) {
            if (s.cliName.equalsIgnoreCase(name)) {
                return s;
            }
        }
        String valid = Arrays.stream(values()).map(s -> s.cliName).collect(Collectors.joining(", "));
        throw MjcException.usage("unknown stage '" + name + "' for --emit (expected one of: " + valid + ")");
    }
}
