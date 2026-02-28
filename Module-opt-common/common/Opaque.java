package common;

import java.util.HashSet;
import java.util.Set;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Methods that use lambdas; the optimizer leaves them untouched. */
public final class Opaque {
    private Opaque() {}

    /** True if the program has a lambda anywhere. */
    public static boolean hasLambda(Goal g) {
        boolean[] found = {false};
        g.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(LambdaExpression n, Void a) { found[0] = true; }
        }, null);
        return found[0];
    }

    /** Ids "Class.method" of opaque methods. */
    public static Set<String> methods(Goal g) {
        Set<String> functionFields = new HashSet<>();
        g.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(ClassDeclaration n, Void a) { fields(n.f3); }
            @Override public void visit(ClassExtendsDeclaration n, Void a) { fields(n.f5); }
            void fields(NodeListOptional vars) {
                for (Node v : vars.nodes) {
                    VarDeclaration vd = (VarDeclaration) v;
                    if (vd.f0.f0.choice instanceof LambdaType) functionFields.add(Ast.name(vd.f1));
                }
            }
        }, null);

        Set<String> result = new HashSet<>();
        g.accept(new GJVoidDepthFirst<Void>() {
            String cls;
            @Override public void visit(ClassDeclaration n, Void a) { cls = Ast.name(n.f1); n.f4.accept(this, a); }
            @Override public void visit(ClassExtendsDeclaration n, Void a) { cls = Ast.name(n.f1); n.f6.accept(this, a); }
            @Override public void visit(MethodDeclaration n, Void a) {
                boolean[] opaque = {n.f1.f0.choice instanceof LambdaType};
                n.accept(new GJVoidDepthFirst<Void>() {
                    @Override public void visit(LambdaExpression e, Void b) { opaque[0] = true; }
                    @Override public void visit(LambdaType t, Void b) { opaque[0] = true; }
                    @Override public void visit(MessageSend m, Void b) {
                        if (Ast.name(m.f2).equals("apply")) opaque[0] = true;
                        super.visit(m, b);
                    }
                    @Override public void visit(Identifier id, Void b) {
                        if (functionFields.contains(Ast.name(id))) opaque[0] = true;
                    }
                }, null);
                if (opaque[0]) result.add(cls + "." + Ast.name(n.f2));
            }
        }, null);
        return result;
    }
}
