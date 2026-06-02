package transform;

import analysis.CallGraph;
import analysis.MonomorphismCheck;
import analysis.SymbolTable;
import common.Ast;
import common.Names;
import common.TreeDumper;
import java.util.*;
import syntaxtree.*;

/** Prints the program with each inlinable call replaced by the renamed callee body. */
public class InlineRewritePass extends TreeDumper {
    private final SymbolTable st;
    private final MonomorphismCheck mc;
    private final Names names;
    private final IdentityHashMap<MethodDeclaration, List<String>> hoisted = new IdentityHashMap<>();
    private final IdentityHashMap<MessageSend, String> sitePrefix = new IdentityHashMap<>();
    private int counter = 0;

    /** Renaming applied while printing an inlined body (null at top level). */
    private record Ctx(String prefix, Set<String> renamed, Set<String> fields) {
        String self() { return prefix + "this"; }
    }
    private Ctx ctx;

    public InlineRewritePass(SymbolTable st, MonomorphismCheck mc, Names names) {
        this.st = st;
        this.mc = mc;
        this.names = names;
        for (SymbolTable.ClassInfo c : st.classes.values())
            for (SymbolTable.MethodInfo m : c.methods.values())
                if (m.ast != null) registerSites(m.ast);
    }

    public String emit(Goal root) {
        StringBuilder sb = new StringBuilder();
        root.accept(this, sb);
        return sb.toString();
    }

    private void registerSites(MethodDeclaration host) {
        List<String> decls = new ArrayList<>();
        host.f8.accept(new visitor.GJVoidDepthFirst<Void>() {
            @Override public void visit(AssignmentStatement a, Void x) {
                if (a.f2.f0.choice instanceof MessageSend ms && mc.inlineTarget.containsKey(ms)) {
                    SymbolTable.MethodInfo callee = mc.inlineTarget.get(ms);
                    String p = names.freshPrefix("inl" + (counter++) + "_");
                    sitePrefix.put(ms, p);
                    callee.locals.forEach((v, t) -> decls.add(t + " " + p + v));
                    for (String[] par : callee.params) decls.add(par[0] + " " + p + par[1]);
                    decls.add(callee.ownerClass + " " + p + "this");
                }
            }
        }, null);
        hoisted.put(host, decls);
    }

    private Ctx contextFor(SymbolTable.MethodInfo callee, String prefix) {
        Set<String> renamed = new HashSet<>(callee.locals.keySet());
        for (String[] p : callee.params) renamed.add(p[1]);
        Set<String> fields = new HashSet<>(st.allFields(callee.ownerClass));
        fields.removeAll(renamed);
        return new Ctx(prefix, renamed, fields);
    }

    @Override
    public void visit(MethodDeclaration n, StringBuilder out) {
        n.f0.accept(this, out); n.f1.accept(this, out); n.f2.accept(this, out);
        n.f3.accept(this, out); n.f4.accept(this, out); n.f5.accept(this, out);
        n.f6.accept(this, out);
        n.f7.accept(this, out);
        for (String d : hoisted.getOrDefault(n, List.of())) {
            int sp = d.lastIndexOf(' ');
            token(d.substring(0, sp), out);
            token(d.substring(sp + 1), out);
            token(";", out);
        }
        n.f8.accept(this, out); n.f9.accept(this, out); n.f10.accept(this, out);
        n.f11.accept(this, out); n.f12.accept(this, out);
    }

    @Override
    public void visit(AssignmentStatement n, StringBuilder out) {
        if (ctx == null && n.f2.f0.choice instanceof MessageSend ms && sitePrefix.containsKey(ms)) {
            inline(Ast.name(n.f0), ms, out);
            return;
        }
        if (ctx == null) { super.visit(n, out); return; }

        String lhs = Ast.name(n.f0);
        if (ctx.fields.contains(lhs)) {                       // f = v;  ->  P_this.f = v;
            token(ctx.self(), out); token(".", out); token(lhs, out);
        } else {
            n.f0.accept(this, out);
        }
        token("=", out);
        String rhs = Ast.idName(Ast.asPrimary(n.f2));
        if (rhs != null && ctx.fields.contains(rhs)) {        // y = f;  ->  y = P_this.f;
            token(ctx.self(), out); token(".", out); token(rhs, out);
        } else {
            n.f2.accept(this, out);
        }
        token(";", out);
    }

    private void inline(String result, MessageSend ms, StringBuilder out) {
        SymbolTable.MethodInfo callee = mc.inlineTarget.get(ms);
        String p = sitePrefix.get(ms);
        token("{", out);
        token(p + "this", out); token("=", out); ms.f0.accept(this, out); token(";", out);
        List<Expression> args = new ArrayList<>();
        if (ms.f4.present()) {
            ExpressionList el = (ExpressionList) ms.f4.node;
            args.add(el.f0);
            for (Node r : el.f1.nodes) args.add(((ExpressionRest) r).f1);
        }
        for (int i = 0; i < callee.params.size(); i++) {
            token(p + callee.params.get(i)[1], out); token("=", out);
            args.get(i).accept(this, out);
            token(";", out);
        }
        ctx = contextFor(callee, p);
        try {
            callee.ast.f8.accept(this, out);
            token(result, out); token("=", out);
            callee.ast.f10.accept(this, out);
            token(";", out);
        } finally {
            ctx = null;
        }
        token("}", out);
    }

    @Override
    public void visit(FieldAssignmentStatement n, StringBuilder out) {
        n.f0.accept(this, out); token(".", out); token(Ast.name(n.f2), out);
        token("=", out); n.f4.accept(this, out); token(";", out);
    }

    @Override
    public void visit(Identifier n, StringBuilder out) {
        String v = Ast.name(n);
        token(ctx != null && ctx.renamed.contains(v) ? ctx.prefix + v : v, out);
    }

    @Override
    public void visit(ThisExpression n, StringBuilder out) {
        token(ctx != null ? ctx.self() : "this", out);
    }

    @Override
    public void visit(MessageSend n, StringBuilder out) {
        n.f0.accept(this, out); token(".", out); token(Ast.name(n.f2), out);
        token("(", out); n.f4.accept(this, out); token(")", out);
    }

    @Override
    public void visit(AllocationExpression n, StringBuilder out) {
        token("new", out); token(Ast.name(n.f1), out); token("(", out); token(")", out);
    }

    @Override
    public void visit(FieldRead n, StringBuilder out) {
        n.f0.accept(this, out); token(".", out); token(Ast.name(n.f2), out);
    }
}
