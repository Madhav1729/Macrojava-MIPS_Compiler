import common.Names;
import common.Opaque;
import myvisitors.MyVisitor;
import myvisitors.SymbolTableVisitor;
import syntaxtree.Goal;

/** MiniJava to three-address MiniJava (CS6013 assignment 2): java P8 < in > out */
public class P8 {
    public static void main(String[] args) throws Exception {
        Goal root = new MiniJavaParser(System.in).Goal();

        SymbolTableVisitor stv = new SymbolTableVisitor();
        root.accept(stv, null);

        MyVisitor mv = new MyVisitor(stv.table, new Names(root), Opaque.methods(root));
        mv.visit(root);
        System.out.print(mv.getOutput());
    }
}
