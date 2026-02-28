package common;

import java.util.List;
import syntaxtree.*;

/** Small helpers for parsing and building MiniJava syntax trees. */
public final class Ast {
    // Choice numbers in the grammar (see minijava.jj).
    public static final int ST_BLOCK = 0, ST_ASSIGN = 1, ST_ARRAY_ASSIGN = 2, ST_IF = 3,
            ST_WHILE = 4, ST_PRINT = 5, ST_FIELD_ASSIGN = 6;
    public static final int EX_MINUS = 5, EX_PRIMARY = 13;
    public static final int PR_INT = 0, PR_TRUE = 1, PR_FALSE = 2, PR_ID = 3, PR_THIS = 4;

    private Ast() {}

    public static Identifier id(String name) {
        return new Identifier(new NodeToken(name));
    }

    public static String name(Identifier id) {
        return id.f0.tokenImage;
    }

    public static PrimaryExpression primaryId(String name) {
        return new PrimaryExpression(new NodeChoice(id(name), PR_ID));
    }

    public static PrimaryExpression primaryInt(int v) {
        if (v < 0) throw new IllegalArgumentException("negative literal " + v);
        return new PrimaryExpression(new NodeChoice(new IntegerLiteral(new NodeToken(String.valueOf(v))), PR_INT));
    }

    public static PrimaryExpression primaryBool(boolean b) {
        return b ? new PrimaryExpression(new NodeChoice(new TrueLiteral(), PR_TRUE))
                 : new PrimaryExpression(new NodeChoice(new FalseLiteral(), PR_FALSE));
    }

    public static Expression expr(PrimaryExpression p) {
        return new Expression(new NodeChoice(p, EX_PRIMARY));
    }

    /** An int constant as an expression; negative values become (0 - n). */
    public static Expression intExpr(int v) {
        if (v >= 0) return expr(primaryInt(v));
        return new Expression(new NodeChoice(new MinusExpression(primaryInt(0), primaryInt(-v)), EX_MINUS));
    }

    public static Statement stmt(Node s, int which) {
        return new Statement(new NodeChoice(s, which));
    }

    public static Statement block(List<? extends Node> stmts) {
        NodeListOptional body = new NodeListOptional();
        for (Node s : stmts) body.addNode(s);
        return stmt(new Block(body), ST_BLOCK);
    }

    /** Name of a Type node: int, boolean, int[], a class name, or Function. */
    public static String typeName(Type t) {
        Node c = t.f0.choice;
        if (c instanceof ArrayType) return "int[]";
        if (c instanceof BooleanType) return "boolean";
        if (c instanceof IntegerType) return "int";
        if (c instanceof Identifier) return name((Identifier) c);
        return "Function";
    }

    /** The primary expression if e is just an atom, else null. */
    public static PrimaryExpression asPrimary(Expression e) {
        return e.f0.choice instanceof PrimaryExpression ? (PrimaryExpression) e.f0.choice : null;
    }

    /** Name if p is a plain identifier, else null. */
    public static String idName(PrimaryExpression p) {
        return p != null && p.f0.choice instanceof Identifier ? name((Identifier) p.f0.choice) : null;
    }
}
