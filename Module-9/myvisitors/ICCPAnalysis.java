package myvisitors;

import common.Ast;
import java.util.*;
import myvisitors.CallGraphBuilder.ClassInfo;
import myvisitors.CallGraphBuilder.MethodInfo;
import myvisitors.ConstantPropagation.Env;
import myvisitors.ConstantPropagation.MethodSummary;
import myvisitors.ConstantPropagation.Value;
import syntaxtree.*;

/** Context-insensitive interprocedural conditional constant propagation. */
public class ICCPAnalysis {
    public final Map<MethodInfo, MethodSummary> summaries = new HashMap<>();
    private final Deque<MethodInfo> worklist = new ArrayDeque<>();
    private Map<String, ClassInfo> classes;

    /** Intraprocedural analyzer whose calls feed the interprocedural state. */
    private class ICCPDataflowAnalyzer extends ConstantPropagation.DataflowAnalyzer {
        ICCPDataflowAnalyzer(MethodInfo m) { super(m); }

        @Override
        protected Env transfer(ConstantPropagation.CFGNode node, Env in) {
            Env out = super.transfer(node, in);
            if (node.astNode instanceof AssignmentStatement a && a.f2.f0.choice instanceof MessageSend ms) {
                Value ret = handleMessageSend(ms, in, method, this);
                String lhs = Ast.name(a.f0);
                if (method.isLocal(lhs)) out.put(lhs, ret);
            }
            return out;
        }
    }

    /** Propagate argument values into every CHA target; return the met return value. */
    private Value handleMessageSend(MessageSend ms, Env in, MethodInfo caller,
                                    ConstantPropagation.DataflowAnalyzer evaluator) {
        String recv = CallGraphBuilder.staticType(ms.f0, caller, classes);
        Set<MethodInfo> targets = CallGraphBuilder.targets(recv, Ast.name(ms.f2), classes);

        List<Value> args = new ArrayList<>();
        if (ms.f4.present()) {
            ExpressionList el = (ExpressionList) ms.f4.node;
            args.add(evaluator.eval(el.f0, in));
            for (Node r : el.f1.nodes) args.add(evaluator.eval(((ExpressionRest) r).f1, in));
        }

        Value merged = Value.top();
        boolean any = false;
        for (MethodInfo callee : targets) {
            MethodSummary cs = summaries.get(callee);
            if (cs == null) continue;
            cs.callerMethods.add(caller);
            if (enter(callee, args) || !cs.isReachable) {
                cs.isReachable = true;
                schedule(callee);
            }
            merged = ConstantPropagation.meet(merged, cs.returnValue);
            any = true;
        }
        return any ? merged : Value.bottom();
    }

    /** Meet argument values into callee's parameter environment; true if it changed. */
    private boolean enter(MethodInfo callee, List<Value> args) {
        MethodSummary cs = summaries.get(callee);
        boolean changed = false;
        for (int i = 0; i < callee.params.size(); i++) {
            String p = callee.params.get(i);
            Value arg = i < args.size() ? args.get(i) : Value.bottom();
            Value old = cs.paramsEnv.get(p);
            Value now = ConstantPropagation.meet(old, arg);
            if (!now.equals(old)) { cs.paramsEnv.put(p, now); changed = true; }
        }
        return changed;
    }

    private void schedule(MethodInfo m) {
        if (!worklist.contains(m)) worklist.add(m);
    }

    /** Main is one println: walk its expression, entering every call it makes. */
    private Value evalMain(Expression e, MethodInfo main, ConstantPropagation.DataflowAnalyzer ev) {
        for (MessageSend ms : CallGraphBuilder.calls(e)) handleMessageSend(ms, new Env(), main, ev);
        return Value.bottom();
    }

    /** Opaque methods are not analyzed: their callees get unknown arguments. */
    private void analyzeOpaque(MethodInfo m) {
        MethodSummary ms = summaries.get(m);
        for (MessageSend call : CallGraphBuilder.calls(m.ast)) {
            for (MethodInfo callee : CallGraphBuilder.targets(null, Ast.name(call.f2), classes)) {
                MethodSummary cs = summaries.get(callee);
                cs.callerMethods.add(m);
                List<Value> unknown = new ArrayList<>();
                for (int i = 0; i < callee.params.size(); i++) unknown.add(Value.bottom());
                if (enter(callee, unknown) || !cs.isReachable) {
                    cs.isReachable = true;
                    schedule(callee);
                }
            }
        }
        setReturn(m, Value.bottom());
    }

    private void setReturn(MethodInfo m, Value v) {
        MethodSummary ms = summaries.get(m);
        Value now = ConstantPropagation.meet(ms.returnValue, v);
        if (!now.equals(ms.returnValue)) {
            ms.returnValue = now;
            for (MethodInfo caller : ms.callerMethods) schedule(caller);
        }
    }

    public void analyzeAndRewrite(Map<String, ClassInfo> classes, MethodInfo main) {
        this.classes = classes;
        for (ClassInfo c : classes.values())
            for (MethodInfo m : c.methods.values()) summaries.put(m, new MethodSummary());

        summaries.get(main).isReachable = true;
        schedule(main);

        while (!worklist.isEmpty()) {
            MethodInfo m = worklist.poll();
            MethodSummary ms = summaries.get(m);
            if (m.isMain()) {
                evalMain(((MainClass) m.ast).f14.f2, m, new ICCPDataflowAnalyzer(m));
            } else if (m.opaque) {
                analyzeOpaque(m);
            } else {
                MethodDeclaration md = (MethodDeclaration) m.ast;
                ICCPDataflowAnalyzer a = new ICCPDataflowAnalyzer(m);
                a.analyze(md.f8, ms.paramsEnv);
                setReturn(m, a.eval(md.f10, a.exitEnv(ms.paramsEnv)));
            }
        }

        for (Map.Entry<MethodInfo, MethodSummary> e : summaries.entrySet()) {
            MethodInfo m = e.getKey();
            MethodSummary ms = e.getValue();
            if (!ms.isReachable || m.opaque) continue;
            ICCPDataflowAnalyzer a = new ICCPDataflowAnalyzer(m);
            if (m.isMain()) {
                ConstantPropagation.foldMain((MainClass) m.ast, a);
            } else {
                MethodDeclaration md = (MethodDeclaration) m.ast;
                a.analyze(md.f8, ms.paramsEnv);
                new ConstantPropagation.Rewriter(a, m).rewriteMethod(md, ms.paramsEnv);
            }
        }
    }
}
