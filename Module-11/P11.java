import common.Opaque;
import common.TreeDumper;
import myvisitors.Refolder;
import syntaxtree.Goal;

/** Folds single-use temps back into expressions: java P11 < in > out */
public class P11 {
    public static void main(String[] args) throws Exception {
        Goal root = new MiniJavaParser(System.in).Goal();
        new Refolder(Opaque.methods(root)).run(root);
        System.out.print(TreeDumper.dump(root));
    }
}
