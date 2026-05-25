package analysis;

import java.util.*;
import syntaxtree.*;

/** Call sites, the static (CHA) call graph and the set of recursive methods. */
public class CallGraph {
    private final SymbolTable st;
    public final Map<String, Set<String>> edges = new LinkedHashMap<>();
    public final Set<String> recursive = new HashSet<>();

    public static class CallSite {
        public final String callerId;
        public final String calleeName;
        public final String receiverDeclType;   // static class of the receiver, or null
        public final List<Expression> args;
        public final AssignmentStatement stmt;  // "x = r.m(...);" in a method body, else null
        public CallSite(String callerId, String calleeName, String receiverDeclType,
                        List<Expression> args, AssignmentStatement stmt) {
            this.callerId = callerId;
            this.calleeName = calleeName;
            this.receiverDeclType = receiverDeclType;
            this.args = args;
            this.stmt = stmt;
        }
        /** Only a call that is a whole statement's right-hand side can be replaced by a body. */
        public boolean inlineCandidate() { return stmt != null; }
    }

    public final IdentityHashMap<MessageSend, CallSite> sites = new IdentityHashMap<>();

    public CallGraph(SymbolTable st) {
        this.st = st;
        for (SymbolTable.ClassInfo c : st.classes.values())
            for (SymbolTable.MethodInfo m : c.methods.values())
                edges.put(m.id, new LinkedHashSet<>());
    }

    public void addEdge(String from, String to) {
        edges.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
    }

    /** CHA: the method every subclass of rDecl runs for mName. */
    public Set<String> staticCallees(String rDecl, String mName) {
        Set<String> out = new LinkedHashSet<>();
        SymbolTable.ClassInfo c = st.cls(rDecl);
        if (c == null) return out;
        for (String d : c.descendants) {
            SymbolTable.MethodInfo m = st.resolveVirtual(d, mName);
            if (m != null) out.add(m.id);
        }
        return out;
    }

    public void computeRecursiveMethods() {
        Map<String, Integer> index = new HashMap<>(), low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] counter = {0};
        for (String v : edges.keySet())
            if (!index.containsKey(v)) strongconnect(v, index, low, stack, onStack, counter);
    }

    private void strongconnect(String v, Map<String, Integer> index, Map<String, Integer> low,
                               Deque<String> stack, Set<String> onStack, int[] counter) {
        index.put(v, counter[0]);
        low.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.add(v);
        for (String w : edges.getOrDefault(v, Collections.emptySet())) {
            if (!index.containsKey(w)) {
                strongconnect(w, index, low, stack, onStack, counter);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            List<String> comp = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                comp.add(w);
            } while (!w.equals(v));
            if (comp.size() > 1) recursive.addAll(comp);
            else if (edges.getOrDefault(v, Collections.emptySet()).contains(v)) recursive.add(v);
        }
    }

    public boolean isRecursive(String methodId) { return recursive.contains(methodId); }
}
