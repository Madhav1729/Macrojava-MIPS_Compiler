package myvisitors;

import common.Ast;
import java.util.*;
import myvisitors.CallGraphBuilder.ClassInfo;
import myvisitors.CallGraphBuilder.MethodInfo;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Removes dead code, constant branches, unused locals and unreachable methods. */
public class DeadCodeEliminator {
    private final Map<String, ClassInfo> classes;
    private final MethodInfo main;
    private final SideEffectAnalysis sea;
    private boolean hasChanged;

    public DeadCodeEliminator(Map<String, ClassInfo> classes, MethodInfo main, SideEffectAnalysis sea) {
        this.classes = classes;
        this.main = main;
        this.sea = sea;
    }

    public void run(Goal root) {
        for (ClassInfo c : classes.values()) {
            for (MethodInfo m : c.methods.values()) {
                if (!m.isMain() && !m.opaque) optimizeMethod((MethodDeclaration) m.ast, m);
            }
        }
        removeDeadMethods(root);
    }

    private void optimizeMethod(MethodDeclaration md, MethodInfo m) {
        do {
            hasChanged = false;
            Set<String> liveOut = readVars(md.f10);
            List<Statement> out = new ArrayList<>();
            processStatementList(md.f8, liveOut, m, out);
            md.f8.nodes.clear();
            md.f8.nodes.addAll(out);
        } while (hasChanged);
        pruneVarDeclarations(md);
    }

    /** Drop declarations of locals that no statement or the return mentions any more. */
    private void pruneVarDeclarations(MethodDeclaration md) {
        Set<String> used = identifiers(md.f8);
        used.addAll(identifiers(md.f10));
        md.f7.nodes.removeIf(n -> !used.contains(Ast.name(((VarDeclaration) n).f1)));
    }

    private record Result(Set<String> liveIn, List<Statement> stmts) {}

    private Set<String> processStatementList(NodeListOptional list, Set<String> liveOut,
                                             MethodInfo m, List<Statement> output) {
        Set<String> live = new HashSet<>(liveOut);
        List<Statement> backwards = new ArrayList<>();
        for (int i = list.nodes.size() - 1; i >= 0; i--) {
            Result r = processStatement((Statement) list.nodes.get(i), live, m);
            live = r.liveIn;
            List<Statement> rev = new ArrayList<>(r.stmts);
            Collections.reverse(rev);
            backwards.addAll(rev);
        }
        Collections.reverse(backwards);
        output.addAll(backwards);
        return live;
    }

    private Result processStatement(Statement stmt, Set<String> liveOut, MethodInfo m) {
        Node c = stmt.f0.choice;

        if (c instanceof Block b) {
            List<Statement> out = new ArrayList<>();
            Set<String> in = processStatementList(b.f1, liveOut, m, out);
            return new Result(in, out);
        }

        if (c instanceof AssignmentStatement a) {
            String lhs = Ast.name(a.f0);
            Set<String> rhsVars = readVars(a.f2);
            if (m.isLocal(lhs) && !liveOut.contains(lhs) && !rhsVars.contains(lhs)
                    && !hasSideEffects(a.f2, m)) {
                hasChanged = true;
                return new Result(new HashSet<>(liveOut), List.of());
            }
            Set<String> in = new HashSet<>(liveOut);
            if (m.isLocal(lhs)) in.remove(lhs);
            in.addAll(rhsVars);
            return new Result(in, List.of(stmt));
        }

        if (c instanceof IfStatement is) {
            Expression cond;
            Statement thenS, elseS;
            if (is.f0.choice instanceof IfthenElseStatement ie) {
                cond = ie.f2; thenS = ie.f4; elseS = ie.f6;
            } else {
                IfthenStatement it = (IfthenStatement) is.f0.choice;
                cond = it.f2; thenS = it.f4; elseS = null;
            }
            Boolean k = literal(cond);
            if (Boolean.TRUE.equals(k)) {
                hasChanged = true;
                return processStatement(thenS, liveOut, m);
            }
            if (Boolean.FALSE.equals(k)) {
                hasChanged = true;
                return elseS == null ? new Result(new HashSet<>(liveOut), List.of())
                                     : processStatement(elseS, liveOut, m);
            }
            Result r1 = processStatement(thenS, liveOut, m);
            Result r2 = elseS == null ? new Result(new HashSet<>(liveOut), List.of())
                                      : processStatement(elseS, liveOut, m);
            if (r1.stmts.isEmpty() && r2.stmts.isEmpty() && !hasSideEffects(cond, m)) {
                hasChanged = true;
                return new Result(new HashSet<>(liveOut), List.of());
            }
            if (is.f0.choice instanceof IfthenElseStatement ie) {
                ie.f4 = wrap(r1.stmts);
                ie.f6 = wrap(r2.stmts);
            } else {
                ((IfthenStatement) is.f0.choice).f4 = wrap(r1.stmts);
            }
            Set<String> in = new HashSet<>(r1.liveIn);
            in.addAll(r2.liveIn);
            in.addAll(readVars(cond));
            return new Result(in, List.of(stmt));
        }

        if (c instanceof WhileStatement w) {
            if (Boolean.FALSE.equals(literal(w.f2))) {
                hasChanged = true;
                return new Result(new HashSet<>(liveOut), List.of());
            }
            Set<String> in = new HashSet<>(liveOut);
            in.addAll(stmtReads(w.f4));
            in.addAll(readVars(w.f2));
            Result r = processStatement(w.f4, in, m);
            w.f4 = wrap(r.stmts);
            in.addAll(r.liveIn);
            return new Result(in, List.of(stmt));
        }

        Set<String> in = new HashSet<>(liveOut);
        in.addAll(stmtReads(stmt));
        return new Result(in, List.of(stmt));
    }

    private static Statement wrap(List<Statement> stmts) {
        return stmts.size() == 1 ? stmts.get(0) : Ast.block(stmts);
    }

    /** true/false if e is that literal, else null. */
    private static Boolean literal(Expression e) {
        PrimaryExpression p = Ast.asPrimary(e);
        if (p == null) return null;
        if (p.f0.choice instanceof TrueLiteral) return true;
        if (p.f0.choice instanceof FalseLiteral) return false;
        if (p.f0.choice instanceof BracketExpression b) return literal(b.f1);
        return null;
    }

    /** Variables read by an expression (method, field and class names excluded). */
    private static Set<String> readVars(Node n) {
        Set<String> vars = new HashSet<>();
        n.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Identifier id, Void a) { vars.add(Ast.name(id)); }
            @Override public void visit(MessageSend ms, Void a) { ms.f0.accept(this, a); ms.f4.accept(this, a); }
            @Override public void visit(FieldRead f, Void a) { f.f0.accept(this, a); }
            @Override public void visit(AllocationExpression x, Void a) { }
        }, null);
        return vars;
    }

    /** Variables read anywhere in a statement (assignment targets excluded). */
    private static Set<String> stmtReads(Statement s) {
        Set<String> vars = new HashSet<>();
        s.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(AssignmentStatement a, Void x) { vars.addAll(readVars(a.f2)); }
            @Override public void visit(ArrayAssignmentStatement a, Void x) {
                vars.add(Ast.name(a.f0)); vars.addAll(readVars(a.f2)); vars.addAll(readVars(a.f5));
            }
            @Override public void visit(FieldAssignmentStatement a, Void x) {
                vars.add(Ast.name(a.f0)); vars.addAll(readVars(a.f4));
            }
            @Override public void visit(Expression e, Void x) { vars.addAll(readVars(e)); }
        }, null);
        return vars;
    }

    private static Set<String> identifiers(Node n) {
        Set<String> names = new HashSet<>();
        n.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Identifier id, Void a) { names.add(Ast.name(id)); }
        }, null);
        return names;
    }

    private boolean messageSendHasSideEffects(MessageSend ms, MethodInfo caller) {
        String recv = CallGraphBuilder.staticType(ms.f0, caller, classes);
        Set<MethodInfo> targets = CallGraphBuilder.targets(recv, Ast.name(ms.f2), classes);
        if (targets.isEmpty()) return true;
        for (MethodInfo t : targets) if (sea.hasSideEffects(t)) return true;
        return false;
    }

    private boolean hasSideEffects(Expression e, MethodInfo m) {
        boolean[] se = {false};
        e.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(MessageSend ms, Void a) {
                if (messageSendHasSideEffects(ms, m)) se[0] = true;
                super.visit(ms, a);
            }
            @Override public void visit(AllocationExpression x, Void a) { se[0] = true; }
            @Override public void visit(ArrayAllocationExpression x, Void a) { se[0] = true; }
            @Override public void visit(LambdaExpression x, Void a) { se[0] = true; }
        }, null);
        return se[0];
    }

    private void removeDeadMethods(Goal root) {
        Set<MethodInfo> reachable = new HashSet<>(List.of(main));
        Deque<MethodInfo> q = new ArrayDeque<>(List.of(main));
        while (!q.isEmpty()) {
            MethodInfo cur = q.poll();
            for (MessageSend ms : CallGraphBuilder.calls(cur.ast)) {
                String recv = cur.opaque ? null : CallGraphBuilder.staticType(ms.f0, cur, classes);
                for (MethodInfo t : CallGraphBuilder.targets(recv, Ast.name(ms.f2), classes)) {
                    if (reachable.add(t)) q.add(t);
                }
            }
        }
        root.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(ClassDeclaration n, Void a) { prune(Ast.name(n.f1), n.f4); }
            @Override public void visit(ClassExtendsDeclaration n, Void a) { prune(Ast.name(n.f1), n.f6); }
            void prune(String cls, NodeListOptional methods) {
                methods.nodes.removeIf(node -> {
                    MethodInfo mi = classes.get(cls).methods.get(Ast.name(((MethodDeclaration) node).f2));
                    return !reachable.contains(mi);
                });
            }
        }, null);
    }
}
