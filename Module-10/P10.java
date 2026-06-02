import analysis.CallGraph;
import analysis.CallGraphPass;
import analysis.MonomorphismCheck;
import analysis.PointsToAnalysis;
import analysis.SymbolTable;
import analysis.SymbolTablePass;
import common.Names;
import common.Opaque;
import common.TreeDumper;
import syntaxtree.Goal;
import transform.InlineRewritePass;

/** Inlines monomorphic calls (CS6013 assignment 4): java P10 [--inline-limit=N] < in > out */
public class P10 {
    public static void main(String[] args) throws Exception {
        int limit = 20;
        for (String a : args) {
            if (a.startsWith("--inline-limit=")) limit = Integer.parseInt(a.substring("--inline-limit=".length()));
        }
        Goal root = new MiniJavaParser(System.in).Goal();
        if (Opaque.hasLambda(root) || !Opaque.methods(root).isEmpty()) {
            System.out.print(TreeDumper.dump(root));
            return;
        }

        SymbolTable st = new SymbolTable();
        root.accept(new SymbolTablePass(st), null);
        st.resolveInheritance();

        CallGraph cg = new CallGraph(st);
        root.accept(new CallGraphPass(st, cg), null);
        cg.computeRecursiveMethods();

        PointsToAnalysis pta = new PointsToAnalysis(st, cg);
        pta.run(root);

        MonomorphismCheck mc = new MonomorphismCheck(st, cg, pta, limit);
        mc.run();

        System.out.print(new InlineRewritePass(st, mc, new Names(root)).emit(root));
    }
}
