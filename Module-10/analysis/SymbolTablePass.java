package analysis;

import common.Ast;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Pass 1: build the symbol table. */
public class SymbolTablePass extends GJVoidDepthFirst<Void> {
    private final SymbolTable st;
    private SymbolTable.ClassInfo cls;
    private SymbolTable.MethodInfo method;

    public SymbolTablePass(SymbolTable st) { this.st = st; }

    @Override
    public void visit(MainClass n, Void a) {
        cls = st.addClass(Ast.name(n.f1));
        st.mainClassName = cls.name;
        st.mainAst = n;
        SymbolTable.MethodInfo main = st.addMethod(cls, "main");
        main.locals.put(Ast.name(n.f11), "String[]");
    }

    @Override
    public void visit(ClassDeclaration n, Void a) {
        cls = st.addClass(Ast.name(n.f1));
        n.f3.accept(this, a);
        n.f4.accept(this, a);
    }

    @Override
    public void visit(ClassExtendsDeclaration n, Void a) {
        cls = st.addClass(Ast.name(n.f1));
        cls.parent = Ast.name(n.f3);
        n.f5.accept(this, a);
        n.f6.accept(this, a);
    }

    @Override
    public void visit(VarDeclaration n, Void a) {
        String type = Ast.typeName(n.f0);
        if (method != null) method.locals.put(Ast.name(n.f1), type);
        else cls.fields.put(Ast.name(n.f1), type);
    }

    @Override
    public void visit(MethodDeclaration n, Void a) {
        method = st.addMethod(cls, Ast.name(n.f2));
        method.ast = n;
        method.returnType = Ast.typeName(n.f1);
        n.f4.accept(this, a);
        n.f7.accept(this, a);
        method = null;
    }

    @Override
    public void visit(FormalParameter n, Void a) {
        method.params.add(new String[] {Ast.typeName(n.f0), Ast.name(n.f1)});
    }
}
