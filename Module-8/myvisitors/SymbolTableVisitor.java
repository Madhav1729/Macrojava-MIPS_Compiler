package myvisitors;

import common.Ast;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Pass 1: collect classes, fields, method signatures and locals. */
public class SymbolTableVisitor extends GJVoidDepthFirst<Void> {
    public final SymbolTable table = new SymbolTable();
    private SymbolTable.ClassInfo cls;
    private SymbolTable.MethodInfo method;

    @Override
    public void visit(MainClass n, Void a) {
        cls = table.addClass(Ast.name(n.f1));
    }

    @Override
    public void visit(ClassDeclaration n, Void a) {
        cls = table.addClass(Ast.name(n.f1));
        n.f3.accept(this, a);
        n.f4.accept(this, a);
    }

    @Override
    public void visit(ClassExtendsDeclaration n, Void a) {
        cls = table.addClass(Ast.name(n.f1));
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
        method = new SymbolTable.MethodInfo(Ast.name(n.f2), cls);
        method.returnType = Ast.typeName(n.f1);
        cls.methods.put(method.name, method);
        n.f4.accept(this, a);
        n.f7.accept(this, a);
        method = null;
    }

    @Override
    public void visit(FormalParameter n, Void a) {
        method.params.put(Ast.name(n.f1), Ast.typeName(n.f0));
    }
}
