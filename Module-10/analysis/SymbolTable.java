package analysis;

import java.util.*;
import syntaxtree.MainClass;
import syntaxtree.MethodDeclaration;

/** Classes (with inheritance), fields, and methods with their params and locals. */
public class SymbolTable {
    public static class ClassInfo {
        public final String name;
        public String parent;
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, MethodInfo> methods = new LinkedHashMap<>();
        public final Set<String> descendants = new LinkedHashSet<>();   // includes itself
        ClassInfo(String name) { this.name = name; }
    }

    public static class MethodInfo {
        public final String id;              // "Class.method"
        public final String name;
        public final String ownerClass;
        public String returnType;
        public final List<String[]> params = new ArrayList<>();          // {type, name}
        public final Map<String, String> locals = new LinkedHashMap<>();
        public MethodDeclaration ast;        // null for main
        MethodInfo(String owner, String name) {
            this.ownerClass = owner;
            this.name = name;
            this.id = owner + "." + name;
        }

        public boolean isLocalOrParam(String v) {
            if (locals.containsKey(v)) return true;
            for (String[] p : params) if (p[1].equals(v)) return true;
            return false;
        }

        public String varType(String v) {
            if (locals.containsKey(v)) return locals.get(v);
            for (String[] p : params) if (p[1].equals(v)) return p[0];
            return null;
        }
    }

    public final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    public String mainClassName;
    public MainClass mainAst;

    public ClassInfo cls(String name) { return name == null ? null : classes.get(name); }

    ClassInfo addClass(String name) {
        ClassInfo c = new ClassInfo(name);
        classes.put(name, c);
        return c;
    }

    MethodInfo addMethod(ClassInfo c, String name) {
        MethodInfo m = new MethodInfo(c.name, name);
        c.methods.put(name, m);
        return m;
    }

    /** Fill in descendants once every class is known. */
    public void resolveInheritance() {
        for (ClassInfo c : classes.values()) {
            for (ClassInfo k = c; k != null; k = cls(k.parent)) k.descendants.add(c.name);
        }
    }

    /** The method an object of exactly class klass runs for name. */
    public MethodInfo resolveVirtual(String klass, String name) {
        for (ClassInfo c = cls(klass); c != null; c = cls(c.parent)) {
            MethodInfo m = c.methods.get(name);
            if (m != null) return m;
        }
        return null;
    }

    public String fieldType(String klass, String field) {
        for (ClassInfo c = cls(klass); c != null; c = cls(c.parent)) {
            if (c.fields.containsKey(field)) return c.fields.get(field);
        }
        return null;
    }

    /** Field names visible in klass (own and inherited). */
    public Set<String> allFields(String klass) {
        Set<String> out = new LinkedHashSet<>();
        for (ClassInfo c = cls(klass); c != null; c = cls(c.parent)) out.addAll(c.fields.keySet());
        return out;
    }

    public boolean isClassType(String t) { return t != null && classes.containsKey(t); }
}
