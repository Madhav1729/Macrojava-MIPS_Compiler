package common;

import java.util.HashSet;
import java.util.Set;
import syntaxtree.Identifier;
import syntaxtree.Node;
import visitor.GJVoidDepthFirst;

/** Fresh identifiers that cannot clash with any name already in the program. */
public class Names {
    private final Set<String> used = new HashSet<>();

    public Names(Node program) {
        program.accept(new GJVoidDepthFirst<Void>() {
            @Override
            public void visit(Identifier n, Void argu) {
                used.add(n.f0.tokenImage);
            }
        }, null);
    }

    /** prefix + smallest number giving an unused name; the name is then reserved. */
    public String fresh(String prefix) {
        for (int i = 0; ; i++) {
            String name = prefix + i;
            if (used.add(name)) return name;
        }
    }

    /** A prefix no existing identifier starts with. */
    public String freshPrefix(String base) {
        for (int i = 0; ; i++) {
            String p = i == 0 ? base : base + i + "_";
            boolean clash = false;
            for (String u : used) {
                if (u.startsWith(p)) { clash = true; break; }
            }
            if (!clash) return p;
        }
    }
}
