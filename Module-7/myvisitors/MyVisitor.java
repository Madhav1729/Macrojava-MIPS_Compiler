package myvisitors;

import common.Ast;
import java.util.*;
import syntaxtree.*;

/** Must-be-initialized analysis: an if keeps what both branches assign, a loop adds nothing. */
public class MyVisitor {
    private final List<String> warnings = new ArrayList<>();
    private String where;
    private Set<String> locals;
    private Set<String> initialized;
    private final Set<String> reported = new HashSet<>();

    public List<String> warnings() { return warnings; }

    public void visit(Goal g) {
        for (Node t : g.f2.nodes) {
            Node c = ((TypeDeclaration) t).f0.choice;
            String cls = c instanceof ClassDeclaration cd ? Ast.name(cd.f1) : Ast.name(((ClassExtendsDeclaration) c).f1);
            NodeListOptional methods = c instanceof ClassDeclaration cd ? cd.f4 : ((ClassExtendsDeclaration) c).f6;
            for (Node m : methods.nodes) method(cls, (MethodDeclaration) m);
        }
    }

    private void method(String cls, MethodDeclaration md) {
        where = cls + "." + Ast.name(md.f2);
        locals = new HashSet<>();
        initialized = new HashSet<>();
        reported.clear();
        for (Node v : md.f7.nodes) locals.add(Ast.name(((VarDeclaration) v).f1));
        for (Node s : md.f8.nodes) statement((Statement) s);
        expression(md.f10, Set.of());
    }

    private void statement(Statement s) {
        Node c = s.f0.choice;
        if (c instanceof Block b) {
            for (Node x : b.f1.nodes) statement((Statement) x);
        } else if (c instanceof AssignmentStatement a) {
            expression(a.f2, Set.of());
            initialized.add(Ast.name(a.f0));
        } else if (c instanceof ArrayAssignmentStatement a) {
            read(Ast.name(a.f0));
            expression(a.f2, Set.of());
            expression(a.f5, Set.of());
        } else if (c instanceof FieldAssignmentStatement a) {
            read(Ast.name(a.f0));
            expression(a.f4, Set.of());
        } else if (c instanceof IfStatement is) {
            if (is.f0.choice instanceof IfthenElseStatement ie) {
                expression(ie.f2, Set.of());
                Set<String> before = new HashSet<>(initialized);
                statement(ie.f4);
                Set<String> afterThen = initialized;
                initialized = new HashSet<>(before);
                statement(ie.f6);
                initialized.retainAll(afterThen);
            } else {
                IfthenStatement it = (IfthenStatement) is.f0.choice;
                expression(it.f2, Set.of());
                Set<String> before = new HashSet<>(initialized);
                statement(it.f4);
                initialized = before;
            }
        } else if (c instanceof WhileStatement w) {
            expression(w.f2, Set.of());
            Set<String> before = new HashSet<>(initialized);
            statement(w.f4);
            initialized = before;
        } else if (c instanceof PrintStatement p) {
            expression(p.f2, Set.of());
        }
    }

    /** Check every variable read in e; bound holds lambda parameters in scope. */
    private void expression(Node e, Set<String> bound) {
        e.accept(new visitor.GJVoidDepthFirst<Void>() {
            @Override public void visit(Identifier id, Void a) {
                String v = Ast.name(id);
                if (!bound.contains(v)) read(v);
            }
            @Override public void visit(MessageSend m, Void a) { m.f0.accept(this, a); m.f4.accept(this, a); }
            @Override public void visit(FieldRead f, Void a) { f.f0.accept(this, a); }
            @Override public void visit(AllocationExpression n, Void a) { }
            @Override public void visit(LambdaExpression l, Void a) {
                Set<String> inner = new HashSet<>(bound);
                inner.add(Ast.name(l.f1));
                expression(l.f4, inner);
            }
        }, null);
    }

    private void read(String v) {
        if (locals.contains(v) && !initialized.contains(v) && reported.add(v)) {
            warnings.add(where + ": variable '" + v + "' may be used before it is assigned");
        }
    }
}
