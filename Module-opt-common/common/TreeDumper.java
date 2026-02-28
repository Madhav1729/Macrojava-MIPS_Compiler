package common;

import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Prints a syntax tree back as MiniJava source. */
public class TreeDumper extends GJVoidDepthFirst<StringBuilder> {
    private int indent = 0;
    private boolean lineStart = true;

    public static String dump(Node n) {
        StringBuilder sb = new StringBuilder();
        n.accept(new TreeDumper(), sb);
        return sb.toString();
    }

    /** Print text as if it were a token of the tree. */
    protected void token(String text, StringBuilder out) {
        visit(new NodeToken(text), out);
    }

    @Override
    public void visit(NodeToken n, StringBuilder out) {
        String tok = n.tokenImage;
        if (tok.isEmpty()) return;                 // <EOF>
        if (tok.equals("}")) indent--;
        if (lineStart) {
            out.append("    ".repeat(Math.max(indent, 0)));
            lineStart = false;
        }
        out.append(tok);
        if (tok.equals("{")) {
            indent++;
            out.append('\n');
            lineStart = true;
        } else if (tok.equals(";") || tok.equals("}")) {
            out.append('\n');
            lineStart = true;
        } else {
            out.append(' ');
        }
    }
}
