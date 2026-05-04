package myvisitors;

import common.Ast;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Lattice, CFG, intraprocedural conditional constant propagation and rewriting. */
public class ConstantPropagation {

    public enum LType { TOP, CONST, BOTTOM }

    public static final class Value {
        public final LType type;
        public final Object val;      // Integer or Boolean when CONST

        private Value(LType type, Object val) { this.type = type; this.val = val; }
        public static Value top() { return new Value(LType.TOP, null); }
        public static Value bottom() { return new Value(LType.BOTTOM, null); }
        public static Value of(Object val) { return new Value(LType.CONST, val); }

        public boolean isConst() { return type == LType.CONST; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Value v) || v.type != type) return false;
            return type != LType.CONST || val.equals(v.val);
        }
        @Override public int hashCode() { return Objects.hash(type, val); }
        @Override public String toString() { return isConst() ? String.valueOf(val) : type.toString(); }
    }

    public static final class Env {
        final Map<String, Value> map = new HashMap<>();

        public Value get(String v) { return map.getOrDefault(v, Value.top()); }
        public void put(String v, Value x) { map.put(v, x); }
        public Env copy() { Env e = new Env(); e.map.putAll(map); return e; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Env other)) return false;
            Set<String> keys = new HashSet<>(map.keySet());
            keys.addAll(other.map.keySet());
            for (String k : keys) if (!get(k).equals(other.get(k))) return false;
            return true;
        }
        @Override public int hashCode() { return 0; }
    }

    public static Value meet(Value a, Value b) {
        if (a.type == LType.TOP) return b;
        if (b.type == LType.TOP) return a;
        if (a.type == LType.BOTTOM || b.type == LType.BOTTOM) return Value.bottom();
        return a.val.equals(b.val) ? a : Value.bottom();
    }

    public static Env meetEnv(Env a, Env b) {
        Env r = new Env();
        Set<String> keys = new HashSet<>(a.map.keySet());
        keys.addAll(b.map.keySet());
        for (String k : keys) r.put(k, meet(a.get(k), b.get(k)));
        return r;
    }

    public static class MethodSummary {
        public Env paramsEnv = new Env();
        public Value returnValue = Value.top();
        public final Set<CallGraphBuilder.MethodInfo> callerMethods = new HashSet<>();
        public boolean isReachable = false;
    }

    public enum NodeType { NORMAL, IF_COND, WHILE_COND, NOP }

    public static class CFGNode {
        public final Node astNode;
        public final NodeType type;
        public final List<CFGNode> preds = new ArrayList<>();
        public final List<CFGNode> succs = new ArrayList<>();
        public CFGNode trueBranch, falseBranch;
        public Env in = new Env();
        public Env out = new Env();
        public boolean reachable;

        CFGNode(Node n, NodeType t) { astNode = n; type = t; }

        void addSucc(CFGNode s) {
            if (!succs.contains(s)) { succs.add(s); s.preds.add(this); }
        }
    }

    protected static class SubGraph {
        final CFGNode entry;
        final List<CFGNode> exits;
        SubGraph(CFGNode e, List<CFGNode> ex) { entry = e; exits = ex; }
        SubGraph(CFGNode n) { this(n, new ArrayList<>(List.of(n))); }
    }

    public static class DataflowAnalyzer {
        public final Map<Node, CFGNode> cfgMap = new IdentityHashMap<>();
        public SubGraph methodCFG;
        protected CallGraphBuilder.MethodInfo method;

        public DataflowAnalyzer(CallGraphBuilder.MethodInfo method) { this.method = method; }

        private CFGNode node(Node key, Node ast, NodeType t) {
            CFGNode n = new CFGNode(ast, t);
            if (key != null) cfgMap.put(key, n);
            return n;
        }

        protected SubGraph buildSequence(NodeListOptional stmts) {
            if (!stmts.present()) return new SubGraph(node(null, null, NodeType.NOP));
            SubGraph head = buildCFG((Statement) stmts.elementAt(0));
            SubGraph cur = head;
            for (int i = 1; i < stmts.size(); i++) {
                SubGraph next = buildCFG((Statement) stmts.elementAt(i));
                for (CFGNode ex : cur.exits) ex.addSucc(next.entry);
                cur = next;
            }
            return new SubGraph(head.entry, cur.exits);
        }

        protected SubGraph buildCFG(Statement stmt) {
            Node c = stmt.f0.choice;
            if (c instanceof IfStatement is) {
                if (is.f0.choice instanceof IfthenElseStatement ie) {
                    CFGNode cond = node(stmt, ie, NodeType.IF_COND);
                    SubGraph t = buildCFG(ie.f4), e = buildCFG(ie.f6);
                    cond.addSucc(t.entry); cond.trueBranch = t.entry;
                    cond.addSucc(e.entry); cond.falseBranch = e.entry;
                    List<CFGNode> exits = new ArrayList<>(t.exits);
                    exits.addAll(e.exits);
                    return new SubGraph(cond, exits);
                }
                IfthenStatement it = (IfthenStatement) is.f0.choice;
                CFGNode cond = node(stmt, it, NodeType.IF_COND);
                SubGraph t = buildCFG(it.f4);
                cond.addSucc(t.entry); cond.trueBranch = t.entry;
                List<CFGNode> exits = new ArrayList<>(t.exits);
                exits.add(cond);                          // false edge falls through
                return new SubGraph(cond, exits);
            }
            if (c instanceof WhileStatement w) {
                CFGNode cond = node(stmt, w, NodeType.WHILE_COND);
                SubGraph body = buildCFG(w.f4);
                cond.addSucc(body.entry); cond.trueBranch = body.entry;
                for (CFGNode ex : body.exits) ex.addSucc(cond);
                return new SubGraph(cond, new ArrayList<>(List.of(cond)));
            }
            if (c instanceof Block b) return buildSequence(b.f1);
            return new SubGraph(node(stmt, c, NodeType.NORMAL));
        }

        public Value eval(Expression e, Env env) {
            Node c = e.f0.choice;
            if (c instanceof PrimaryExpression p) return evalPrimary(p, env);
            if (c instanceof AndExpression x) return binop("&&", x.f0, x.f2, env);
            if (c instanceof OrExpression x) return binop("||", x.f0, x.f2, env);
            if (c instanceof CompareExpression x) return binop("<=", x.f0, x.f2, env);
            if (c instanceof neqExpression x) return binop("!=", x.f0, x.f2, env);
            if (c instanceof AddExpression x) return binop("+", x.f0, x.f2, env);
            if (c instanceof MinusExpression x) return binop("-", x.f0, x.f2, env);
            if (c instanceof TimesExpression x) return binop("*", x.f0, x.f2, env);
            if (c instanceof DivExpression x) return binop("/", x.f0, x.f2, env);
            return Value.bottom();
        }

        public Value evalPrimary(PrimaryExpression p, Env env) {
            Node c = p.f0.choice;
            if (c instanceof IntegerLiteral i) {
                try { return Value.of(Integer.parseInt(i.f0.tokenImage)); }
                catch (NumberFormatException ex) { return Value.bottom(); }
            }
            if (c instanceof TrueLiteral) return Value.of(true);
            if (c instanceof FalseLiteral) return Value.of(false);
            if (c instanceof Identifier id) {
                String v = Ast.name(id);
                return method.isLocal(v) ? env.get(v) : Value.bottom();
            }
            if (c instanceof NotExpression n) {
                Value v = eval(n.f1, env);
                if (v.isConst() && v.val instanceof Boolean b) return Value.of(!b);
                return v.type == LType.TOP ? Value.top() : Value.bottom();
            }
            if (c instanceof BracketExpression b) return eval(b.f1, env);
            return Value.bottom();
        }

        private Value binop(String op, PrimaryExpression l, PrimaryExpression r, Env env) {
            Value a = evalPrimary(l, env), b = evalPrimary(r, env);
            if (a.type == LType.TOP || b.type == LType.TOP) return Value.top();
            if (a.type == LType.BOTTOM || b.type == LType.BOTTOM) return Value.bottom();
            if (a.val instanceof Integer x && b.val instanceof Integer y) {
                switch (op) {
                    case "+": return Value.of(x + y);
                    case "-": return Value.of(x - y);
                    case "*": return Value.of(x * y);
                    case "/": return y == 0 ? Value.bottom() : Value.of(x / y);
                    case "<=": return Value.of(x <= y);
                    case "!=": return Value.of(!x.equals(y));
                }
            }
            if (a.val instanceof Boolean x && b.val instanceof Boolean y) {
                switch (op) {
                    case "&&": return Value.of(x && y);
                    case "||": return Value.of(x || y);
                    case "!=": return Value.of(!x.equals(y));
                }
            }
            return Value.bottom();
        }

        protected Env transfer(CFGNode node, Env in) {
            Env out = in.copy();
            if (node.astNode instanceof AssignmentStatement a) {
                String lhs = Ast.name(a.f0);
                if (method.isLocal(lhs)) out.put(lhs, eval(a.f2, in));
            }
            return out;
        }

        private Value condition(CFGNode s) {
            Expression cond = s.astNode instanceof IfthenElseStatement ie ? ie.f2
                    : s.astNode instanceof IfthenStatement it ? it.f2
                    : ((WhileStatement) s.astNode).f2;
            return eval(cond, s.in);
        }

        public void analyze(NodeListOptional statements, Env initial) {
            if (!statements.present()) return;
            methodCFG = buildSequence(statements);
            Deque<CFGNode> worklist = new ArrayDeque<>();
            methodCFG.entry.reachable = true;
            methodCFG.entry.in = initial.copy();
            worklist.add(methodCFG.entry);

            while (!worklist.isEmpty()) {
                CFGNode s = worklist.poll();
                Env newIn = null;
                for (CFGNode p : s.preds) {
                    if (!p.reachable) continue;
                    newIn = newIn == null ? p.out.copy() : meetEnv(newIn, p.out);
                }
                if (s == methodCFG.entry) newIn = newIn == null ? initial.copy() : meetEnv(newIn, initial);
                if (newIn == null) newIn = initial.copy();
                s.in = newIn;

                Env newOut = transfer(s, s.in);
                boolean changed = !newOut.equals(s.out);
                if (changed) s.out = newOut;

                List<CFGNode> active = new ArrayList<>();
                if (s.type == NodeType.IF_COND || s.type == NodeType.WHILE_COND) {
                    Value v = condition(s);
                    if (v.isConst() && Boolean.TRUE.equals(v.val)) {
                        active.add(s.trueBranch);
                    } else if (v.isConst() && Boolean.FALSE.equals(v.val)) {
                        if (s.falseBranch != null) active.add(s.falseBranch);
                        else for (CFGNode x : s.succs) if (x != s.trueBranch) active.add(x);
                    } else {
                        active.addAll(s.succs);
                    }
                } else {
                    active.addAll(s.succs);
                }
                for (CFGNode x : active) {
                    if (!x.reachable) {
                        x.reachable = true;
                        if (!worklist.contains(x)) worklist.add(x);
                    } else if (changed && !worklist.contains(x)) {
                        worklist.add(x);
                    }
                }
            }
        }

        /** Meet of the OUT states of all reachable exits (the state at "return"). */
        public Env exitEnv(Env fallback) {
            Env r = null;
            if (methodCFG != null) {
                for (CFGNode ex : methodCFG.exits) {
                    if (!ex.reachable) continue;
                    r = r == null ? ex.out.copy() : meetEnv(r, ex.out);
                }
            }
            return r != null ? r : fallback.copy();
        }
    }

    /** Replaces local reads that hold a known constant; folds constant right-hand sides. */
    public static class Rewriter {
        private final DataflowAnalyzer analyzer;
        private final CallGraphBuilder.MethodInfo method;

        public Rewriter(DataflowAnalyzer analyzer, CallGraphBuilder.MethodInfo method) {
            this.analyzer = analyzer;
            this.method = method;
        }

        public void rewriteMethod(MethodDeclaration md, Env params) {
            for (Node s : md.f8.nodes) statement((Statement) s, params);
            expression(md.f10, analyzer.exitEnv(params));
        }

        private void statement(Statement s, Env env) {
            CFGNode cfg = analyzer.cfgMap.get(s);
            if (cfg != null && !cfg.reachable) return;
            Env in = cfg != null ? cfg.in : env;
            Node c = s.f0.choice;
            if (c instanceof Block b) {
                for (Node x : b.f1.nodes) statement((Statement) x, in);
            } else if (c instanceof AssignmentStatement a) {
                Value v = analyzer.eval(a.f2, in);
                if (v.isConst() && !(v.val instanceof Integer i && i == Integer.MIN_VALUE)) {
                    a.f2 = constExpression(v);
                } else {
                    operands(a.f2, in);
                }
            } else if (c instanceof ArrayAssignmentStatement a) {
                expression(a.f2, in);
                expression(a.f5, in);
            } else if (c instanceof FieldAssignmentStatement a) {
                expression(a.f4, in);
            } else if (c instanceof PrintStatement p) {
                expression(p.f2, in);
            } else if (c instanceof IfStatement is) {
                if (is.f0.choice instanceof IfthenElseStatement ie) {
                    expression(ie.f2, in);
                    statement(ie.f4, in);
                    statement(ie.f6, in);
                } else {
                    IfthenStatement it = (IfthenStatement) is.f0.choice;
                    expression(it.f2, in);
                    statement(it.f4, in);
                }
            } else if (c instanceof WhileStatement w) {
                expression(w.f2, in);
                statement(w.f4, in);
            }
        }

        /** A whole-expression position: replace it if it is a constant local. */
        private void expression(Expression e, Env env) {
            PrimaryExpression p = Ast.asPrimary(e);
            String v = Ast.idName(p);
            if (v != null && method.isLocal(v)) {
                Value x = env.get(v);
                if (x.isConst() && !(x.val instanceof Integer i && i == Integer.MIN_VALUE)) {
                    e.f0 = constExpression(x).f0;
                    return;
                }
            }
            operands(e, env);
        }

        /** Replace constant locals among the operands of e (never array bases or receivers). */
        private void operands(Expression e, Env env) {
            Node c = e.f0.choice;
            if (c instanceof AndExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof OrExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof CompareExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof neqExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof AddExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof MinusExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof TimesExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof DivExpression x) { operand(x.f0, env); operand(x.f2, env); }
            else if (c instanceof ArrayLookup x) operand(x.f2, env);
            else if (c instanceof MessageSend m && m.f4.present()) {
                ExpressionList el = (ExpressionList) m.f4.node;
                expression(el.f0, env);
                for (Node r : el.f1.nodes) expression(((ExpressionRest) r).f1, env);
            } else if (c instanceof PrimaryExpression p) {
                Node pc = p.f0.choice;
                if (pc instanceof ArrayAllocationExpression a) expression(a.f3, env);
                else if (pc instanceof NotExpression n) expression(n.f1, env);
                else if (pc instanceof BracketExpression b) expression(b.f1, env);
            }
        }

        private void operand(PrimaryExpression p, Env env) {
            String v = Ast.idName(p);
            if (v == null || !method.isLocal(v)) {
                if (p.f0.choice instanceof BracketExpression b) expression(b.f1, env);
                return;
            }
            Value x = env.get(v);
            if (!x.isConst() || (x.val instanceof Integer i && i == Integer.MIN_VALUE)) return;
            p.f0 = constPrimary(x).f0;
        }
    }

    public static Expression constExpression(Value v) {
        if (v.val instanceof Boolean b) return Ast.expr(Ast.primaryBool(b));
        return Ast.intExpr((Integer) v.val);
    }

    /** A constant in a primary position; negatives become (0 - n). */
    public static PrimaryExpression constPrimary(Value v) {
        if (v.val instanceof Boolean b) return Ast.primaryBool(b);
        int i = (Integer) v.val;
        if (i >= 0) return Ast.primaryInt(i);
        return new PrimaryExpression(new NodeChoice(new BracketExpression(Ast.intExpr(i)), 8));
    }

    /** Fold constant pure subexpressions of main's println argument. */
    public static void foldMain(MainClass mc, DataflowAnalyzer analyzer) {
        Env empty = new Env();
        mc.f14.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Expression e, Void a) {
                Value v = analyzer.eval(e, empty);
                if (v.isConst() && !(v.val instanceof Integer i && i == Integer.MIN_VALUE)
                        && !(e.f0.choice instanceof PrimaryExpression p
                             && (p.f0.choice instanceof IntegerLiteral || p.f0.choice instanceof TrueLiteral
                                 || p.f0.choice instanceof FalseLiteral))) {
                    e.f0 = constExpression(v).f0;
                    return;
                }
                super.visit(e, a);
            }
        }, null);
    }
}
