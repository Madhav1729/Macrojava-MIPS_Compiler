package myvisitors;

import common.Ast;
import common.Opaque;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Folds single-use temps into their use, and "c = e; while (c) {...; c = e;}" into "while (e)". */
public class Refolder {
    private final Set<String> opaque;

    public Refolder(Set<String> opaque) { this.opaque = opaque; }

    public void run(Goal g) {
        g.accept(new GJVoidDepthFirst<String>() {
            @Override public void visit(ClassDeclaration n, String a) { n.f4.accept(this, Ast.name(n.f1)); }
            @Override public void visit(ClassExtendsDeclaration n, String a) { n.f6.accept(this, Ast.name(n.f1)); }
            @Override public void visit(MethodDeclaration n, String cls) {
                if (!opaque.contains(cls + "." + Ast.name(n.f2))) method(n);
            }
        }, null);
    }

    private Set<String> locals;
    private Map<String, Integer> defs, uses;

    private void method(MethodDeclaration md) {
        locals = new HashSet<>();
        for (Node v : md.f7.nodes) locals.add(Ast.name(((VarDeclaration) v).f1));
        boolean changed;
        do {
            count(md);
            changed = foldList(md.f8.nodes, md);
            count(md);
            changed |= foldLoops(md.f8);
        } while (changed);
        Set<String> used = new HashSet<>();
        md.f8.accept(identifiers(used), null);
        md.f10.accept(identifiers(used), null);
        md.f7.nodes.removeIf(n -> !used.contains(Ast.name(((VarDeclaration) n).f1)));
    }

    private void count(MethodDeclaration md) {
        defs = new HashMap<>();
        uses = new HashMap<>();
        md.f8.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(AssignmentStatement a, Void x) {
                defs.merge(Ast.name(a.f0), 1, Integer::sum);
                a.f2.accept(this, x);
            }
            @Override public void visit(Identifier id, Void x) { uses.merge(Ast.name(id), 1, Integer::sum); }
            @Override public void visit(MessageSend m, Void x) { m.f0.accept(this, x); m.f4.accept(this, x); }
            @Override public void visit(FieldRead f, Void x) { f.f0.accept(this, x); }
            @Override public void visit(AllocationExpression n, Void x) { }
        }, null);
        md.f10.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Identifier id, Void x) { uses.merge(Ast.name(id), 1, Integer::sum); }
            @Override public void visit(MessageSend m, Void x) { m.f0.accept(this, x); m.f4.accept(this, x); }
            @Override public void visit(FieldRead f, Void x) { f.f0.accept(this, x); }
            @Override public void visit(AllocationExpression n, Void x) { }
        }, null);
    }

    /** Fold within one statement list (and, recursively, nested lists). */
    private boolean foldList(Vector<Node> stmts, MethodDeclaration md) {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = (Statement) stmts.get(i);
            changed |= foldNested(s, md);
            if (!(s.f0.choice instanceof AssignmentStatement a)) continue;
            String t = Ast.name(a.f0);
            if (!locals.contains(t) || defs.getOrDefault(t, 0) != 1 || uses.getOrDefault(t, 0) != 1) continue;
            Statement next = i + 1 < stmts.size() ? (Statement) stmts.get(i + 1) : null;
            if (next != null && renameTarget(next, t, a.f2)) {
                stmts.remove(i);
                i = Math.max(-1, i - 2);
                changed = true;
                count(md);
                continue;
            }
            List<Node> slots = next != null ? evaluated(next)
                                            : stmts == md.f8.nodes ? List.of(md.f10) : null;
            if (slots == null) continue;
            if (substitute(slots, t, a.f2)) {
                stmts.remove(i);
                i = Math.max(-1, i - 2);
                changed = true;
                count(md);
            }
        }
        return changed;
    }

    private boolean foldNested(Statement s, MethodDeclaration md) {
        Node c = s.f0.choice;
        if (c instanceof Block b) return foldList(b.f1.nodes, md);
        if (c instanceof IfStatement is) {
            if (is.f0.choice instanceof IfthenElseStatement ie) return foldNested(ie.f4, md) | foldNested(ie.f6, md);
            return foldNested(((IfthenStatement) is.f0.choice).f4, md);
        }
        if (c instanceof WhileStatement w) return foldNested(w.f4, md);
        return false;
    }

    /** "t = v; t[i] = x;" becomes "v[i] = x;" (same for t.f = x). */
    private static boolean renameTarget(Statement next, String t, Expression e) {
        String v = Ast.idName(Ast.asPrimary(e));
        if (v == null) return false;
        Node c = next.f0.choice;
        if (c instanceof ArrayAssignmentStatement a && Ast.name(a.f0).equals(t)) {
            a.f0 = Ast.id(v);
            return true;
        }
        if (c instanceof FieldAssignmentStatement f && Ast.name(f.f0).equals(t)) {
            f.f0 = Ast.id(v);
            return true;
        }
        return false;
    }

    /** The expressions a statement evaluates once, in order (not branch or loop bodies). */
    private static List<Node> evaluated(Statement s) {
        Node c = s.f0.choice;
        if (c instanceof AssignmentStatement a) return List.of(a.f2);
        if (c instanceof ArrayAssignmentStatement a) return List.of(a.f2, a.f5);
        if (c instanceof FieldAssignmentStatement a) return List.of(a.f4);
        if (c instanceof PrintStatement p) return List.of(p.f2);
        if (c instanceof IfStatement is) {
            return List.of(is.f0.choice instanceof IfthenElseStatement ie ? ie.f2 : ((IfthenStatement) is.f0.choice).f2);
        }
        return null;
    }

    /** Replace the read of t by e if nothing evaluated before it can interfere. */
    private boolean substitute(List<Node> slots, String t, Expression e) {
        boolean impure = hasEffects(e);
        Deque<Node> order = new ArrayDeque<>();
        for (Node n : slots) order.addAll(evaluationOrder(n));
        for (Node n : order) {
            if (n instanceof PrimaryExpression p && t.equals(Ast.idName(p))) return replace(p, e);
            if (n instanceof Expression x && Ast.asPrimary(x) != null && t.equals(Ast.idName(Ast.asPrimary(x)))) {
                if (slots.contains(x)) x.f0 = e.f0;
                else replace(Ast.asPrimary(x), e);
                return true;
            }
            if (!safeBefore(n, impure)) return false;
        }
        return false;
    }

    /** May n be evaluated before the moved expression? */
    private boolean safeBefore(Node n, boolean movedIsImpure) {
        if (n instanceof MessageSend || n instanceof AllocationExpression || n instanceof ArrayAllocationExpression)
            return false;
        if (!movedIsImpure) return true;
        if (n instanceof PrimaryExpression p) {
            Node c = p.f0.choice;
            if (c instanceof Identifier id) return locals.contains(Ast.name(id));
            return c instanceof IntegerLiteral || c instanceof TrueLiteral || c instanceof FalseLiteral
                    || c instanceof ThisExpression || c instanceof BracketExpression || c instanceof NotExpression;
        }
        if (n instanceof FieldRead || n instanceof ArrayLookup || n instanceof ArrayLength) return false;
        return true;
    }

    /** Put e where primary p (a read of the temp) is: bare if e is atomic, else in brackets. */
    private static boolean replace(PrimaryExpression p, Expression e) {
        PrimaryExpression ep = Ast.asPrimary(e);
        if (ep != null) p.f0 = ep.f0;
        else p.f0 = new NodeChoice(new BracketExpression(e), 8);
        return true;
    }

    /** Pre-order list of expression nodes in Java evaluation order. */
    private static List<Node> evaluationOrder(Node root) {
        List<Node> out = new ArrayList<>();
        root.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(Expression e, Void a) { out.add(e); super.visit(e, a); }
            @Override public void visit(PrimaryExpression p, Void a) { out.add(p); super.visit(p, a); }
            @Override public void visit(MessageSend m, Void a) {
                m.f0.accept(this, a); m.f4.accept(this, a); out.add(m);
            }
            @Override public void visit(AllocationExpression n, Void a) { out.add(n); }
            @Override public void visit(ArrayAllocationExpression n, Void a) { n.f3.accept(this, a); out.add(n); }
            @Override public void visit(FieldRead f, Void a) { out.add(f); }
            @Override public void visit(ArrayLookup l, Void a) { super.visit(l, a); out.add(l); }
            @Override public void visit(ArrayLength l, Void a) { super.visit(l, a); out.add(l); }
        }, null);
        return out;
    }

    private static boolean hasEffects(Node e) {
        boolean[] r = {false};
        e.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(MessageSend m, Void a) { r[0] = true; }
            @Override public void visit(AllocationExpression n, Void a) { r[0] = true; }
            @Override public void visit(ArrayAllocationExpression n, Void a) { r[0] = true; }
        }, null);
        return r[0];
    }

    private boolean foldLoops(NodeListOptional list) {
        boolean changed = false;
        Vector<Node> stmts = list.nodes;
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = (Statement) stmts.get(i);
            Node c = s.f0.choice;
            if (c instanceof Block b) changed |= foldLoops(b.f1);
            else if (c instanceof IfStatement is) {
                if (is.f0.choice instanceof IfthenElseStatement ie) changed |= foldLoopsIn(ie.f4) | foldLoopsIn(ie.f6);
                else changed |= foldLoopsIn(((IfthenStatement) is.f0.choice).f4);
            } else if (c instanceof WhileStatement w) {
                changed |= foldLoopsIn(w.f4);
                if (i > 0 && foldLoop(stmts, i - 1, w)) {
                    stmts.remove(i - 1);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean foldLoopsIn(Statement s) {
        if (s.f0.choice instanceof Block b) return foldLoops(b.f1);
        NodeListOptional one = new NodeListOptional(s);
        return foldLoops(one);
    }

    /** Fold the loop if the entry condition equals the end-of-body one, up to known constants. */
    private boolean foldLoop(Vector<Node> stmts, int at, WhileStatement w) {
        Statement prev = (Statement) stmts.get(at);
        if (!(prev.f0.choice instanceof AssignmentStatement init)) return false;
        String c = Ast.name(init.f0);
        if (!c.equals(Ast.idName(Ast.asPrimary(w.f2))) || !locals.contains(c)) return false;
        if (defs.getOrDefault(c, 0) != 2 || uses.getOrDefault(c, 0) != 1) return false;
        if (!(w.f4.f0.choice instanceof Block body) || body.f1.nodes.isEmpty()) return false;
        Statement last = (Statement) body.f1.nodes.lastElement();
        if (!(last.f0.choice instanceof AssignmentStatement again) || !Ast.name(again.f0).equals(c)) return false;
        Map<String, String> subst = new HashMap<>();
        if (!unify(init.f2, again.f2, subst)) return false;
        for (Map.Entry<String, String> e : subst.entrySet()) {
            if (!holdsBefore(stmts, at, e.getKey(), e.getValue())) return false;
        }
        w.f2 = again.f2;
        body.f1.nodes.remove(body.f1.nodes.size() - 1);
        return true;
    }

    /** Same tree, except that where b reads a local, a may hold a literal. */
    private boolean unify(Node a, Node b, Map<String, String> subst) {
        if (b instanceof Expression eb && a instanceof Expression ea) {
            String v = Ast.idName(Ast.asPrimary(eb));
            if (v != null && locals.contains(v) && isLiteral(ea)) return record(subst, v, ea);
        }
        if (b instanceof PrimaryExpression pb && a instanceof PrimaryExpression pa) {
            String v = Ast.idName(pb);
            if (v != null && locals.contains(v) && isLiteral(pa)) return record(subst, v, pa);
        }
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof NodeToken ta) return ta.tokenImage.equals(((NodeToken) b).tokenImage);
        if (a instanceof NodeChoice ca) {
            NodeChoice cb = (NodeChoice) b;
            return ca.which == cb.which && unify(ca.choice, cb.choice, subst);
        }
        if (a instanceof NodeOptional oa) {
            NodeOptional ob = (NodeOptional) b;
            if (oa.present() != ob.present()) return false;
            return !oa.present() || unify(oa.node, ob.node, subst);
        }
        if (a instanceof NodeListInterface la) {
            NodeListInterface lb = (NodeListInterface) b;
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) if (!unify(la.elementAt(i), lb.elementAt(i), subst)) return false;
            return true;
        }
        for (java.lang.reflect.Field f : a.getClass().getFields()) {
            if (!Node.class.isAssignableFrom(f.getType())) continue;
            try {
                if (!unify((Node) f.get(a), (Node) f.get(b), subst)) return false;
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean record(Map<String, String> subst, String v, Node literal) {
        String text = common.TreeDumper.dump(literal).strip();
        String old = subst.putIfAbsent(v, text);
        return old == null || old.equals(text);
    }

    /** An integer or boolean literal, possibly written (0 - n). */
    private static boolean isLiteral(Node n) {
        if (n instanceof Expression e) {
            if (e.f0.choice instanceof MinusExpression m) return isLiteral(m.f0) && isLiteral(m.f2);
            return Ast.asPrimary(e) != null && isLiteral(Ast.asPrimary(e));
        }
        if (n instanceof PrimaryExpression p) {
            Node c = p.f0.choice;
            if (c instanceof BracketExpression b) return isLiteral(b.f1);
            return c instanceof IntegerLiteral || c instanceof TrueLiteral || c instanceof FalseLiteral;
        }
        return false;
    }

    /** Does local v hold the literal (text) right after stmts[0..at-1]? */
    private boolean holdsBefore(Vector<Node> stmts, int at, String v, String literal) {
        for (int i = at - 1; i >= 0; i--) {
            Statement s = (Statement) stmts.get(i);
            if (s.f0.choice instanceof AssignmentStatement a && Ast.name(a.f0).equals(v)) {
                return common.TreeDumper.dump(a.f2).strip().equals(literal);
            }
            if (writes(s, v)) return false;
        }
        return false;
    }

    private static boolean writes(Statement s, String v) {
        boolean[] w = {false};
        s.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(AssignmentStatement a, Void x) { if (Ast.name(a.f0).equals(v)) w[0] = true; }
        }, null);
        return w[0];
    }

    private static GJVoidDepthFirst<Void> identifiers(Set<String> into) {
        return new GJVoidDepthFirst<>() {
            @Override public void visit(Identifier id, Void a) { into.add(Ast.name(id)); }
        };
    }
}
