package myvisitors;

import common.Ast;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Symbol table, class hierarchy and the CHA call graph. */
public class CallGraphBuilder {

    public static class ClassInfo {
        public final String name;
        public String parentName;
        public ClassInfo parent;
        public final List<ClassInfo> children = new ArrayList<>();
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, MethodInfo> methods = new LinkedHashMap<>();
        public ClassInfo(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public static class MethodInfo {
        public final String name;
        public final ClassInfo owner;
        public String returnType;
        public final List<String> params = new ArrayList<>();
        public final Map<String, String> locals = new HashMap<>();   // params and locals
        public Node ast;                                             // MethodDeclaration or MainClass
        public boolean opaque;                                       // see common.Opaque
        public MethodInfo(String name, ClassInfo owner) { this.name = name; this.owner = owner; }

        public boolean isMain() { return ast instanceof MainClass; }
        public boolean isLocal(String v) { return locals.containsKey(v); }
        @Override public String toString() { return owner.name + "." + name; }
        @Override public int hashCode() { return toString().hashCode(); }
        @Override public boolean equals(Object o) {
            return o instanceof MethodInfo && toString().equals(o.toString());
        }
    }

    public static class CallGraph {
        public final Map<MethodInfo, Set<MethodInfo>> edges = new HashMap<>();
        public final Map<MethodInfo, Set<MethodInfo>> callers = new HashMap<>();

        public void addEdge(MethodInfo caller, MethodInfo callee) {
            edges.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
            callers.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
        }
    }

    public static class SymbolTableBuilder extends GJVoidDepthFirst<Void> {
        public final Map<String, ClassInfo> classes = new LinkedHashMap<>();
        public MethodInfo main;
        private ClassInfo currentClass;
        private MethodInfo currentMethod;
        private final Set<String> opaque;

        public SymbolTableBuilder(Set<String> opaque) { this.opaque = opaque; }

        @Override
        public void visit(MainClass n, Void argu) {
            currentClass = new ClassInfo(Ast.name(n.f1));
            classes.put(currentClass.name, currentClass);
            main = new MethodInfo("main", currentClass);
            main.ast = n;
            main.locals.put(Ast.name(n.f11), "String[]");
            currentClass.methods.put("main", main);
        }

        @Override
        public void visit(ClassDeclaration n, Void argu) {
            currentClass = new ClassInfo(Ast.name(n.f1));
            classes.put(currentClass.name, currentClass);
            n.f3.accept(this, argu);
            n.f4.accept(this, argu);
        }

        @Override
        public void visit(ClassExtendsDeclaration n, Void argu) {
            currentClass = new ClassInfo(Ast.name(n.f1));
            currentClass.parentName = Ast.name(n.f3);
            classes.put(currentClass.name, currentClass);
            n.f5.accept(this, argu);
            n.f6.accept(this, argu);
        }

        @Override
        public void visit(VarDeclaration n, Void argu) {
            String type = Ast.typeName(n.f0);
            if (currentMethod != null) currentMethod.locals.put(Ast.name(n.f1), type);
            else currentClass.fields.put(Ast.name(n.f1), type);
        }

        @Override
        public void visit(MethodDeclaration n, Void argu) {
            currentMethod = new MethodInfo(Ast.name(n.f2), currentClass);
            currentMethod.ast = n;
            currentMethod.returnType = Ast.typeName(n.f1);
            currentMethod.opaque = opaque.contains(currentClass.name + "." + currentMethod.name);
            currentClass.methods.put(currentMethod.name, currentMethod);
            n.f4.accept(this, argu);
            n.f7.accept(this, argu);
            currentMethod = null;
        }

        @Override
        public void visit(FormalParameter n, Void argu) {
            String p = Ast.name(n.f1);
            currentMethod.params.add(p);
            currentMethod.locals.put(p, Ast.typeName(n.f0));
        }

        public void resolveHierarchy() {
            for (ClassInfo c : classes.values()) {
                if (c.parentName != null) {
                    ClassInfo p = classes.get(c.parentName);
                    if (p != null) { c.parent = p; p.children.add(c); }
                }
            }
        }
    }

    /** Static class of a primary expression inside method m, or null if unknown. */
    public static String staticType(PrimaryExpression p, MethodInfo m, Map<String, ClassInfo> classes) {
        Node c = p.f0.choice;
        if (c instanceof ThisExpression) return m.owner.name;
        if (c instanceof AllocationExpression a) return Ast.name(a.f1);
        if (c instanceof Identifier id) return varType(Ast.name(id), m);
        if (c instanceof BracketExpression b) return staticType(b.f1, m, classes);
        return null;
    }

    public static String staticType(Expression e, MethodInfo m, Map<String, ClassInfo> classes) {
        Node c = e.f0.choice;
        if (c instanceof PrimaryExpression p) return staticType(p, m, classes);
        if (c instanceof MessageSend ms) {
            String recv = staticType(ms.f0, m, classes);
            MethodInfo target = recv == null ? null : resolve(classes.get(recv), Ast.name(ms.f2));
            return target == null ? null : target.returnType;
        }
        return null;
    }

    /** Type of a local, parameter or (inherited) field. */
    public static String varType(String v, MethodInfo m) {
        if (m.locals.containsKey(v)) return m.locals.get(v);
        for (ClassInfo c = m.owner; c != null; c = c.parent) {
            if (c.fields.containsKey(v)) return c.fields.get(v);
        }
        return null;
    }

    /** The method m that a call on an object of exactly class c runs. */
    public static MethodInfo resolve(ClassInfo c, String name) {
        for (ClassInfo k = c; k != null; k = k.parent) {
            MethodInfo mi = k.methods.get(name);
            if (mi != null) return mi;
        }
        return null;
    }

    /** CHA targets of recv.name(); an unknown receiver type means every method of that name. */
    public static Set<MethodInfo> targets(String staticType, String name, Map<String, ClassInfo> classes) {
        Set<MethodInfo> result = new LinkedHashSet<>();
        ClassInfo root = staticType == null ? null : classes.get(staticType);
        if (root == null) {
            for (ClassInfo c : classes.values()) {
                MethodInfo mi = c.methods.get(name);
                if (mi != null && !mi.isMain()) result.add(mi);
            }
            return result;
        }
        Deque<ClassInfo> q = new ArrayDeque<>(List.of(root));
        while (!q.isEmpty()) {
            ClassInfo c = q.poll();
            MethodInfo mi = resolve(c, name);
            if (mi != null) result.add(mi);
            q.addAll(c.children);
        }
        return result;
    }

    /** Every call in node n (including nested calls and lambda bodies). */
    public static List<MessageSend> calls(Node n) {
        List<MessageSend> out = new ArrayList<>();
        n.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(MessageSend ms, Void a) { out.add(ms); super.visit(ms, a); }
        }, null);
        return out;
    }

    public static CallGraph buildCallGraph(Map<String, ClassInfo> classes) {
        CallGraph cg = new CallGraph();
        for (ClassInfo c : classes.values()) {
            for (MethodInfo m : c.methods.values()) {
                for (MessageSend ms : calls(m.ast)) {
                    String recv = m.opaque ? null : staticType(ms.f0, m, classes);
                    for (MethodInfo t : targets(recv, Ast.name(ms.f2), classes)) cg.addEdge(m, t);
                }
            }
        }
        return cg;
    }
}
