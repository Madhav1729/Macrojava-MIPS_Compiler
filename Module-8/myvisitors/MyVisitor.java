package myvisitors;

import common.Ast;
import common.Names;
import common.TreeDumper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import syntaxtree.*;

/** Emits three-address MiniJava; fields are read only as "x = f;". */
public class MyVisitor {
    /** An emitted value: its text and static type. */
    record Pair(String var, String type) {}

    private final SymbolTable st;
    private final Names names;
    private final Set<String> opaque;
    private final StringBuilder out = new StringBuilder();

    private SymbolTable.ClassInfo cls;
    private SymbolTable.MethodInfo method;
    private StringBuilder body;
    private List<String> temps;
    private int indent;

    public MyVisitor(SymbolTable st, Names names, Set<String> opaque) {
        this.st = st;
        this.names = names;
        this.opaque = opaque;
    }

    public String getOutput() { return out.toString(); }

    public void visit(Goal g) {
        if (g.f0.present()) out.append(TreeDumper.dump(g.f0.node));
        out.append(TreeDumper.dump(g.f1));
        for (Node t : g.f2.nodes) {
            Node c = ((TypeDeclaration) t).f0.choice;
            if (c instanceof ClassDeclaration cd) {
                emitClass(Ast.name(cd.f1), null, cd.f3, cd.f4);
            } else {
                ClassExtendsDeclaration ce = (ClassExtendsDeclaration) c;
                emitClass(Ast.name(ce.f1), Ast.name(ce.f3), ce.f5, ce.f6);
            }
        }
    }

    private void emitClass(String name, String parent, NodeListOptional fields, NodeListOptional methods) {
        cls = st.classes.get(name);
        out.append("class ").append(name);
        if (parent != null) out.append(" extends ").append(parent);
        out.append(" {\n");
        for (Node f : fields.nodes) out.append("    ").append(TreeDumper.dump(f));
        for (Node m : methods.nodes) emitMethod((MethodDeclaration) m);
        out.append("}\n");
    }

    private void emitMethod(MethodDeclaration n) {
        String mname = Ast.name(n.f2);
        if (opaque.contains(cls.name + "." + mname)) {
            out.append(TreeDumper.dump(n));
            return;
        }
        method = cls.methods.get(mname);
        body = new StringBuilder();
        temps = new ArrayList<>();
        indent = 2;

        for (Node s : n.f8.nodes) statement((Statement) s);
        String ret = atom(n.f10);

        out.append("    public ").append(method.returnType).append(' ').append(mname).append('(');
        List<String> ps = new ArrayList<>();
        method.params.forEach((p, t) -> ps.add(t + " " + p));
        out.append(String.join(", ", ps)).append(") {\n");
        method.locals.forEach((v, t) -> out.append("        ").append(t).append(' ').append(v).append(";\n"));
        for (String t : temps) out.append("        ").append(t).append(";\n");
        out.append(body);
        out.append("        return ").append(ret).append(";\n");
        out.append("    }\n");
        method = null;
    }

    private void emit(String line) {
        body.append("    ".repeat(indent)).append(line).append('\n');
    }

    private void statement(Statement s) {
        Node c = s.f0.choice;
        if (c instanceof Block b) {
            for (Node x : b.f1.nodes) statement((Statement) x);
        } else if (c instanceof AssignmentStatement a) {
            String lhs = Ast.name(a.f0);
            if (method.isLocal(lhs)) emit(lhs + " = " + rhs(a.f2) + ";");
            else emit(lhs + " = " + atom(a.f2) + ";");
        } else if (c instanceof ArrayAssignmentStatement a) {
            String arr = value(a.f0).var;
            String idx = atom(a.f2);
            String val = atom(a.f5);
            emit(arr + "[" + idx + "] = " + val + ";");
        } else if (c instanceof FieldAssignmentStatement a) {
            String obj = value(a.f0).var;
            emit(obj + "." + Ast.name(a.f2) + " = " + atom(a.f4) + ";");
        } else if (c instanceof IfStatement i) {
            if (i.f0.choice instanceof IfthenElseStatement ie) {
                String cond = atom(ie.f2);
                emit("if (" + cond + ") {");
                nested(ie.f4);
                emit("} else {");
                nested(ie.f6);
                emit("}");
            } else {
                IfthenStatement it = (IfthenStatement) i.f0.choice;
                String cond = atom(it.f2);
                emit("if (" + cond + ") {");
                nested(it.f4);
                emit("}");
            }
        } else if (c instanceof WhileStatement w && simpleCondition(w.f2)) {
            emit("while (" + rhs(w.f2) + ") {");
            nested(w.f4);
            emit("}");
        } else if (c instanceof WhileStatement w) {
            String cond = temp("boolean");
            emit(cond + " = " + rhs(w.f2) + ";");
            emit("while (" + cond + ") {");
            indent++;
            statement(w.f4);
            emit(cond + " = " + rhs(w.f2) + ";");
            indent--;
            emit("}");
        } else if (c instanceof PrintStatement p) {
            emit("System.out.println(" + atom(p.f2) + ");");
        }
    }

    /** A local or literal, or one operator applied to locals and literals. */
    private boolean simpleCondition(Expression e) {
        Node c = e.f0.choice;
        if (c instanceof PrimaryExpression p && p.f0.choice instanceof BracketExpression b) return simpleCondition(b.f1);
        if (c instanceof PrimaryExpression p) return plainAtom(p);
        if (c instanceof CompareExpression x) return plainAtom(x.f0) && plainAtom(x.f2);
        if (c instanceof neqExpression x) return plainAtom(x.f0) && plainAtom(x.f2);
        return false;
    }

    private boolean plainAtom(PrimaryExpression p) {
        Node c = p.f0.choice;
        if (c instanceof BracketExpression b && b.f1.f0.choice instanceof PrimaryExpression q) return plainAtom(q);
        if (c instanceof Identifier id) return method.isLocal(Ast.name(id));
        return c instanceof IntegerLiteral || c instanceof TrueLiteral || c instanceof FalseLiteral;
    }

    private void nested(Statement s) {
        indent++;
        statement(s);
        indent--;
    }

    private String temp(String type) {
        String t = names.fresh("t");
        temps.add(type + " " + t);
        return t;
    }

    /** Value of e as an atom, emitting the code that computes it. */
    private String atom(Expression e) {
        Pair p = expression(e, null);
        return p.var;
    }

    /** Value of e as a simple right-hand side (one operator at most). */
    private String rhs(Expression e) {
        StringBuilder r = new StringBuilder();
        expression(e, r);
        return r.toString();
    }

    /** Emit code for e; with rhs set, write the last operation there instead of a temp. */
    private Pair expression(Expression e, StringBuilder rhs) {
        Node c = e.f0.choice;
        if (c instanceof PrimaryExpression p) return primary(p, rhs);
        if (c instanceof AndExpression a) return shortCircuit(a.f0, a.f2, true, rhs);
        if (c instanceof OrExpression o) return shortCircuit(o.f0, o.f2, false, rhs);
        if (c instanceof CompareExpression x) return binary(x.f0, "<=", x.f2, "boolean", rhs);
        if (c instanceof neqExpression x) return binary(x.f0, "!=", x.f2, "boolean", rhs);
        if (c instanceof AddExpression x) return binary(x.f0, "+", x.f2, "int", rhs);
        if (c instanceof MinusExpression x) return binary(x.f0, "-", x.f2, "int", rhs);
        if (c instanceof TimesExpression x) return binary(x.f0, "*", x.f2, "int", rhs);
        if (c instanceof DivExpression x) return binary(x.f0, "/", x.f2, "int", rhs);
        if (c instanceof ArrayLookup x) {
            String arr = primary(x.f0).var;
            String idx = primary(x.f2).var;
            return simple(arr + "[" + idx + "]", "int", rhs);
        }
        if (c instanceof ArrayLength x) {
            return simple(primary(x.f0).var + ".length", "int", rhs);
        }
        if (c instanceof MessageSend m) {
            Pair recv = primary(m.f0);
            List<String> args = new ArrayList<>();
            if (m.f4.present()) {
                ExpressionList el = (ExpressionList) m.f4.node;
                args.add(atom(el.f0));
                for (Node r : el.f1.nodes) args.add(atom(((ExpressionRest) r).f1));
            }
            String name = Ast.name(m.f2);
            String type = st.returnType(recv.type, name);
            return simple(recv.var + "." + name + "(" + String.join(", ", args) + ")", type, rhs);
        }
        if (c instanceof FieldRead f) {
            Pair obj = value(f.f0);
            String fname = Ast.name(f.f2);
            return simple(obj.var + "." + fname, st.fieldType(obj.type, fname), rhs);
        }
        throw new IllegalStateException("unexpected expression in a non-opaque method: " + c.getClass().getSimpleName());
    }

    private Pair binary(PrimaryExpression l, String op, PrimaryExpression r, String type, StringBuilder rhs) {
        String a = primary(l).var;
        String b = primary(r).var;
        return simple(a + " " + op + " " + b, type, rhs);
    }

    /** Short-circuit && / ||: the right operand is computed only when needed. */
    private Pair shortCircuit(PrimaryExpression l, PrimaryExpression r, boolean and, StringBuilder rhs) {
        String a = primary(l).var;
        String t = temp("boolean");
        emit("if (" + a + ") {");
        indent++;
        if (and) emit(t + " = " + primaryRhs(r) + ";");
        else emit(t + " = true;");
        indent--;
        emit("} else {");
        indent++;
        if (and) emit(t + " = false;");
        else emit(t + " = " + primaryRhs(r) + ";");
        indent--;
        emit("}");
        return finish(new Pair(t, "boolean"), rhs);
    }

    /** A one-operator expression: hand it to the caller as rhs, or store it in a temp. */
    private Pair simple(String text, String type, StringBuilder rhs) {
        if (rhs != null) {
            rhs.append(text);
            return new Pair(text, type);
        }
        String t = temp(type);
        emit(t + " = " + text + ";");
        return new Pair(t, type);
    }

    private Pair finish(Pair atom, StringBuilder rhs) {
        if (rhs != null) rhs.append(atom.var);
        return atom;
    }

    private Pair primary(PrimaryExpression p) {
        return primary(p, null);
    }

    /** p as a one-operator right-hand side (its sub-computations are emitted first). */
    private String primaryRhs(PrimaryExpression p) {
        StringBuilder r = new StringBuilder();
        primary(p, r);
        return r.toString();
    }

    private Pair primary(PrimaryExpression p, StringBuilder rhs) {
        Node c = p.f0.choice;
        if (c instanceof IntegerLiteral i) return finish(new Pair(i.f0.tokenImage, "int"), rhs);
        if (c instanceof TrueLiteral) return finish(new Pair("true", "boolean"), rhs);
        if (c instanceof FalseLiteral) return finish(new Pair("false", "boolean"), rhs);
        if (c instanceof ThisExpression) return finish(new Pair("this", cls.name), rhs);
        if (c instanceof Identifier id) {
            String v = Ast.name(id);
            if (rhs != null && !method.isLocal(v)) {
                rhs.append(v);
                return new Pair(v, st.fieldType(cls.name, v));
            }
            return finish(value(id), rhs);
        }
        if (c instanceof ArrayAllocationExpression a) {
            String len = atom(a.f3);
            return simple("new int[" + len + "]", "int[]", rhs);
        }
        if (c instanceof AllocationExpression a) {
            String k = Ast.name(a.f1);
            return simple("new " + k + "()", k, rhs);
        }
        if (c instanceof NotExpression x) {
            String v = atom(x.f1);
            return simple("!" + v, "boolean", rhs);
        }
        if (c instanceof BracketExpression b) return expression(b.f1, rhs);
        throw new IllegalStateException("unexpected primary " + c.getClass().getSimpleName());
    }

    /** A variable as an atom: locals as they are, fields copied into a temp. */
    private Pair value(Identifier id) {
        String v = Ast.name(id);
        if (method.isLocal(v)) {
            String t = method.params.containsKey(v) ? method.params.get(v) : method.locals.get(v);
            return new Pair(v, t);
        }
        String type = st.fieldType(cls.name, v);
        String t = temp(type);
        emit(t + " = " + v + ";");
        return new Pair(t, type);
    }
}
