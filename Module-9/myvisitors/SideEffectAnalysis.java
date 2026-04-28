package myvisitors;

import common.Ast;
import java.util.*;
import myvisitors.CallGraphBuilder.ClassInfo;
import myvisitors.CallGraphBuilder.MethodInfo;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Methods that print, write memory or allocate, and their callers. */
public class SideEffectAnalysis {
    public final Map<MethodInfo, Boolean> sideEffectsMap = new HashMap<>();

    /** Phase 1: does the method body itself do something observable? */
    private static boolean locallyImpure(MethodInfo m) {
        if (m.isMain() || m.opaque) return true;
        boolean[] impure = {false};
        m.ast.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(PrintStatement n, Void a) { impure[0] = true; }
            @Override public void visit(ArrayAssignmentStatement n, Void a) { impure[0] = true; }
            @Override public void visit(FieldAssignmentStatement n, Void a) { impure[0] = true; }
            @Override public void visit(AllocationExpression n, Void a) { impure[0] = true; }
            @Override public void visit(ArrayAllocationExpression n, Void a) { impure[0] = true; }
            @Override public void visit(AssignmentStatement n, Void a) {
                if (!m.isLocal(Ast.name(n.f0))) impure[0] = true;    // "f = e;" writes a field
                super.visit(n, a);
            }
        }, null);
        return impure[0];
    }

    /** Phase 2: a caller of an impure method is impure (worklist over CHA edges). */
    public void analyze(Map<String, ClassInfo> classes, CallGraphBuilder.CallGraph cg) {
        Deque<MethodInfo> worklist = new ArrayDeque<>();
        for (ClassInfo c : classes.values()) {
            for (MethodInfo m : c.methods.values()) {
                boolean se = locallyImpure(m);
                sideEffectsMap.put(m, se);
                if (se) worklist.add(m);
            }
        }
        while (!worklist.isEmpty()) {
            MethodInfo callee = worklist.poll();
            for (MethodInfo caller : cg.callers.getOrDefault(callee, Set.of())) {
                if (!sideEffectsMap.get(caller)) {
                    sideEffectsMap.put(caller, true);
                    worklist.add(caller);
                }
            }
        }
    }

    public boolean hasSideEffects(MethodInfo m) {
        return sideEffectsMap.getOrDefault(m, true);
    }
}
