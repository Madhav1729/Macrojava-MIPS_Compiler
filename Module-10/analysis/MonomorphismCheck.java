package analysis;

import common.Ast;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Inline a call iff it has one runtime callee, which is non-recursive, not main and small. */
public class MonomorphismCheck {
    private final SymbolTable st;
    private final CallGraph cg;
    private final PointsToAnalysis pta;
    private final int sizeLimit;
    public final IdentityHashMap<MessageSend, SymbolTable.MethodInfo> inlineTarget = new IdentityHashMap<>();

    public MonomorphismCheck(SymbolTable st, CallGraph cg, PointsToAnalysis pta, int sizeLimit) {
        this.st = st;
        this.cg = cg;
        this.pta = pta;
        this.sizeLimit = sizeLimit;
    }

    public void run() {
        for (Map.Entry<MessageSend, CallGraph.CallSite> e : cg.sites.entrySet()) {
            MessageSend ms = e.getKey();
            CallGraph.CallSite cs = e.getValue();
            if (!cs.inlineCandidate()) continue;

            Set<String> recvPts = pta.receiverPtsAt(ms);
            if (recvPts.isEmpty()) continue;
            Set<String> calleeIds = new HashSet<>();
            SymbolTable.MethodInfo callee = null;
            for (String site : recvPts) {
                String klass = pta.allocSiteClass(site);
                SymbolTable.MethodInfo m = klass == null ? null : st.resolveVirtual(klass, cs.calleeName);
                if (m == null) { calleeIds.clear(); callee = null; break; }
                calleeIds.add(m.id);
                callee = m;
            }
            if (callee == null || calleeIds.size() != 1) continue;     // polymorphic
            if (callee.ast == null || cg.isRecursive(callee.id)) continue;
            if (size(callee.ast) > sizeLimit || !qualifiable(callee)) continue;
            inlineTarget.put(ms, callee);
        }
    }

    /** Number of statements in a method body (nested ones included). */
    static int size(MethodDeclaration md) {
        int[] n = {0};
        md.f8.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Statement s, Void a) {
                if (!(s.f0.choice instanceof Block)) n[0]++;
                super.visit(s, a);
            }
        }, null);
        return n[0];
    }

    /** Fields may appear only as "x = f;" or "f = atom;", the forms the rewriter can qualify. */
    private boolean qualifiable(SymbolTable.MethodInfo callee) {
        Set<String> fields = new HashSet<>(st.allFields(callee.ownerClass));
        fields.removeIf(callee::isLocalOrParam);
        boolean[] ok = {true};
        GJVoidDepthFirst<Void> v = new GJVoidDepthFirst<>() {
            @Override public void visit(AssignmentStatement a, Void x) {
                boolean fieldLhs = fields.contains(Ast.name(a.f0));
                PrimaryExpression p = Ast.asPrimary(a.f2);
                String rhsVar = Ast.idName(p);
                if (rhsVar != null && fields.contains(rhsVar)) {
                    if (fieldLhs) ok[0] = false;            // f = g;
                    return;                                 // x = f;
                }
                if (fieldLhs && (p == null || !atom(p))) ok[0] = false;
                a.f2.accept(this, x);
            }
            @Override public void visit(MessageSend m, Void x) { m.f0.accept(this, x); m.f4.accept(this, x); }
            @Override public void visit(FieldRead f, Void x) { f.f0.accept(this, x); }
            @Override public void visit(FieldAssignmentStatement f, Void x) { f.f0.accept(this, x); f.f4.accept(this, x); }
            @Override public void visit(AllocationExpression n, Void x) { }
            @Override public void visit(Identifier id, Void x) {
                if (fields.contains(Ast.name(id))) ok[0] = false;
            }
        };
        callee.ast.f8.accept(v, null);
        PrimaryExpression ret = Ast.asPrimary(callee.ast.f10);
        String retVar = Ast.idName(ret);
        if (retVar != null && fields.contains(retVar)) return false;
        callee.ast.f10.accept(v, null);
        return ok[0];
    }

    private static boolean atom(PrimaryExpression p) {
        Node c = p.f0.choice;
        return c instanceof Identifier || c instanceof IntegerLiteral || c instanceof TrueLiteral
                || c instanceof FalseLiteral || c instanceof ThisExpression
                || (c instanceof BracketExpression b && b.f1.f0.choice instanceof MinusExpression);
    }
}
