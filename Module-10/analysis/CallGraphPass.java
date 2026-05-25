package analysis;

import common.Ast;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Pass 2: record every call site and the static call graph edges. */
public class CallGraphPass extends GJVoidDepthFirst<SymbolTable.MethodInfo> {
    private final SymbolTable st;
    private final CallGraph cg;
    private AssignmentStatement pendingStmt;   // set while visiting "x = r.m(...)"

    public CallGraphPass(SymbolTable st, CallGraph cg) {
        this.st = st;
        this.cg = cg;
    }

    @Override
    public void visit(MainClass n, SymbolTable.MethodInfo m) {
        n.f14.accept(this, st.cls(st.mainClassName).methods.get("main"));
    }

    @Override
    public void visit(ClassDeclaration n, SymbolTable.MethodInfo m) {
        visitMethods(Ast.name(n.f1), n.f4);
    }

    @Override
    public void visit(ClassExtendsDeclaration n, SymbolTable.MethodInfo m) {
        visitMethods(Ast.name(n.f1), n.f6);
    }

    private void visitMethods(String cls, NodeListOptional methods) {
        for (Node n : methods.nodes) {
            MethodDeclaration md = (MethodDeclaration) n;
            SymbolTable.MethodInfo mi = st.cls(cls).methods.get(Ast.name(md.f2));
            md.f8.accept(this, mi);
            md.f10.accept(this, mi);
        }
    }

    @Override
    public void visit(AssignmentStatement n, SymbolTable.MethodInfo m) {
        if (n.f2.f0.choice instanceof MessageSend) pendingStmt = n;
        n.f2.accept(this, m);
    }

    @Override
    public void visit(MessageSend n, SymbolTable.MethodInfo caller) {
        AssignmentStatement stmt = pendingStmt;
        pendingStmt = null;
        String recvDecl = receiverType(n.f0, caller);
        String callee = Ast.name(n.f2);

        List<Expression> args = new ArrayList<>();
        if (n.f4.present()) {
            ExpressionList el = (ExpressionList) n.f4.node;
            args.add(el.f0);
            for (Node r : el.f1.nodes) args.add(((ExpressionRest) r).f1);
        }
        boolean statementSite = stmt != null && stmt.f2.f0.choice == n && caller.ast != null;
        cg.sites.put(n, new CallGraph.CallSite(caller.id, callee, recvDecl, args, statementSite ? stmt : null));

        if (recvDecl != null)
            for (String t : cg.staticCallees(recvDecl, callee)) cg.addEdge(caller.id, t);

        n.f0.accept(this, caller);
        n.f4.accept(this, caller);
    }

    /** Static class of a receiver expression (identifier, this, new C(), nested call). */
    String receiverType(PrimaryExpression p, SymbolTable.MethodInfo caller) {
        Node c = p.f0.choice;
        if (c instanceof ThisExpression) return caller.ownerClass;
        if (c instanceof AllocationExpression a) return Ast.name(a.f1);
        if (c instanceof Identifier id) {
            String v = Ast.name(id);
            String t = caller.varType(v);
            return t != null ? t : st.fieldType(caller.ownerClass, v);
        }
        if (c instanceof BracketExpression b && b.f1.f0.choice instanceof PrimaryExpression q)
            return receiverType(q, caller);
        if (c instanceof BracketExpression b && b.f1.f0.choice instanceof MessageSend ms) {
            String r = receiverType(ms.f0, caller);
            SymbolTable.MethodInfo t = r == null ? null : st.resolveVirtual(r, Ast.name(ms.f2));
            return t == null ? null : t.returnType;
        }
        return null;
    }
}
