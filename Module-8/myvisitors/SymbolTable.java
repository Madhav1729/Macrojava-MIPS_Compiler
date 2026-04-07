package myvisitors;

import java.util.LinkedHashMap;
import java.util.Map;

/** Classes, fields, methods and local types of a MiniJava program. */
public class SymbolTable {
    public static class ClassInfo {
        public final String name;
        public String parent;
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, MethodInfo> methods = new LinkedHashMap<>();
        ClassInfo(String name) { this.name = name; }
    }

    public static class MethodInfo {
        public final String name;
        public final ClassInfo owner;
        public String returnType;
        public final Map<String, String> params = new LinkedHashMap<>();
        public final Map<String, String> locals = new LinkedHashMap<>();
        MethodInfo(String name, ClassInfo owner) { this.name = name; this.owner = owner; }

        public boolean isLocal(String v) { return params.containsKey(v) || locals.containsKey(v); }
    }

    public final Map<String, ClassInfo> classes = new LinkedHashMap<>();

    ClassInfo addClass(String name) {
        ClassInfo c = new ClassInfo(name);
        classes.put(name, c);
        return c;
    }

    /** Declared type of a field as seen from class cls, or null. */
    public String fieldType(String cls, String f) {
        for (ClassInfo c = classes.get(cls); c != null; c = c.parent == null ? null : classes.get(c.parent)) {
            if (c.fields.containsKey(f)) return c.fields.get(f);
        }
        return null;
    }

    /** Return type of method m called on static type cls, or null. */
    public String returnType(String cls, String m) {
        for (ClassInfo c = classes.get(cls); c != null; c = c.parent == null ? null : classes.get(c.parent)) {
            MethodInfo mi = c.methods.get(m);
            if (mi != null) return mi.returnType;
        }
        return null;
    }
}
