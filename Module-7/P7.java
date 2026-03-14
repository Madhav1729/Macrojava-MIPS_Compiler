import myvisitors.MyVisitor;
import syntaxtree.Goal;

/** Warns about locals read before assignment (CS6013 assignment 1): java P7 < in */
public class P7 {
    public static void main(String[] args) throws Exception {
        Goal root = new MiniJavaParser(System.in).Goal();
        MyVisitor v = new MyVisitor();
        v.visit(root);
        for (String w : v.warnings()) System.out.println(w);
    }
}
