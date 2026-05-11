import common.Opaque;
import common.TreeDumper;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import myvisitors.CallGraphBuilder;
import myvisitors.DeadCodeEliminator;
import myvisitors.ICCPAnalysis;
import myvisitors.SideEffectAnalysis;
import syntaxtree.Goal;

/** Constant propagation and dead-code elimination (CS6013 assignment 3), repeated until stable: java P9 < in > out */
public class P9 {
    private static final int MAX_ROUNDS = 100;

    public static void main(String[] args) throws Exception {
        String current = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        String prev = null;
        for (int round = 0; round < MAX_ROUNDS && !current.equals(prev); round++) {
            prev = current;
            current = runOnePipeline(prev);
        }
        System.out.print(current);
    }

    private static String runOnePipeline(String source) throws ParseException {
        Goal root = new MiniJavaParser(new StringReader(source)).Goal();

        CallGraphBuilder.SymbolTableBuilder st = new CallGraphBuilder.SymbolTableBuilder(Opaque.methods(root));
        root.accept(st, null);
        st.resolveHierarchy();

        CallGraphBuilder.CallGraph cg = CallGraphBuilder.buildCallGraph(st.classes);

        SideEffectAnalysis sea = new SideEffectAnalysis();
        sea.analyze(st.classes, cg);

        new ICCPAnalysis().analyzeAndRewrite(st.classes, st.main);

        new DeadCodeEliminator(st.classes, st.main, sea).run(root);

        return TreeDumper.dump(root);
    }
}
