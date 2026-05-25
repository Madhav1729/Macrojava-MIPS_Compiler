package analysis;

import common.Ast;
import java.util.*;
import syntaxtree.*;
import visitor.GJVoidDepthFirst;

/** Value-context based, flow-sensitive points-to analysis with a global field-sensitive heap. */
public class PointsToAnalysis {
    private final SymbolTable st;
    private final CallGraph cg;

    private final IdentityHashMap<AllocationExpression, String> allocId = new IdentityHashMap<>();
    private final Map<String, String> allocClass = new HashMap<>();
    private int allocCounter = 0;

    private final Map<String, Map<String, Set<String>>> heap = new HashMap<>();
    private final Map<String, Set<Summary>> heapReaders = new HashMap<>();   // "site.field" -> readers

    private final Map<String, Map<Context, Summary>> summaries = new HashMap<>();
    private final Deque<Summary> work = new ArrayDeque<>();
    private final Set<Summary> inWork = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Summary, Set<Summary>> deps = new IdentityHashMap<>();

    public final IdentityHashMap<MessageSend, Set<String>> recvPtsAtSite = new IdentityHashMap<>();

    private SymbolTable.MethodInfo curMi;
    private Summary curSummary;

    public PointsToAnalysis(SymbolTable st, CallGraph cg) { this.st = st; this.cg = cg; }

    public String allocSiteClass(String site) { return allocClass.get(site); }

    public Set<String> receiverPtsAt(MessageSend ms) {
        return recvPtsAtSite.getOrDefault(ms, Collections.emptySet());
    }

    public void run(Goal root) {
        root.accept(new GJVoidDepthFirst<Void>() {
            @Override public void visit(AllocationExpression n, Void a) {
                String klass = Ast.name(n.f1);
                String id = "alloc" + (allocCounter++) + "_" + klass;
                allocId.put(n, id);
                allocClass.put(id, klass);
            }
        }, null);

        enqueue(summaryOf(st.mainClassName + ".main", new Context(List.of())));
        while (!work.isEmpty()) {
            Summary s = work.pollFirst();
            inWork.remove(s);
            analyzeSummary(s);
        }
    }

    private static final class Context {
        final List<Set<String>> sets;
        Context(List<Set<String>> s) {
            List<Set<String>> ss = new ArrayList<>();
            for (Set<String> x : s) ss.add(Collections.unmodifiableSet(new TreeSet<>(x)));
            sets = Collections.unmodifiableList(ss);
        }
        @Override public boolean equals(Object o) { return o instanceof Context c && c.sets.equals(sets); }
        @Override public int hashCode() { return sets.hashCode(); }
    }

    private static final class Summary {
        final String methodId;
        final Context ctx;
        final Set<String> returnPts = new LinkedHashSet<>();
        Summary(String methodId, Context ctx) { this.methodId = methodId; this.ctx = ctx; }
    }

    private Summary summaryOf(String methodId, Context ctx) {
        Map<Context, Summary> byCtx = summaries.computeIfAbsent(methodId, k -> new HashMap<>());
        Summary s = byCtx.get(ctx);
        if (s == null) {
            s = new Summary(methodId, ctx);
            byCtx.put(ctx, s);
            enqueue(s);
        }
        return s;
    }

    private void enqueue(Summary s) { if (inWork.add(s)) work.addLast(s); }

    private void addDep(Summary caller, Summary callee) {
        deps.computeIfAbsent(callee, k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(caller);
    }

    private void refireDeps(Summary callee) {
        for (Summary c : deps.getOrDefault(callee, Set.of())) enqueue(c);
    }

    private Set<String> heapGet(String site, String field) {
        if (curSummary != null)
            heapReaders.computeIfAbsent(site + "." + field, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                       .add(curSummary);
        Map<String, Set<String>> m = heap.get(site);
        Set<String> s = m == null ? null : m.get(field);
        return s == null ? Collections.emptySet() : s;
    }

    private void heapAdd(String site, String field, Set<String> pts) {
        if (pts == null || pts.isEmpty()) return;
        Set<String> s = heap.computeIfAbsent(site, k -> new HashMap<>()).computeIfAbsent(field, k -> new LinkedHashSet<>());
        if (s.addAll(pts)) {
            for (Summary r : heapReaders.getOrDefault(site + "." + field, Set.of())) enqueue(r);
        }
    }

    private SymbolTable.MethodInfo methodOf(String id) {
        int dot = id.indexOf('.');
        SymbolTable.ClassInfo c = st.cls(id.substring(0, dot));
        return c == null ? null : c.methods.get(id.substring(dot + 1));
    }

    private boolean isLocal(String v) { return curMi != null && curMi.isLocalOrParam(v); }

    private boolean isField(String v) {
        return curMi != null && !isLocal(v) && st.fieldType(curMi.ownerClass, v) != null;
    }

    private Map<String, Set<String>> initialFact(SymbolTable.MethodInfo mi, Context ctx) {
        Map<String, Set<String>> fact = new HashMap<>();
        if (!ctx.sets.isEmpty()) fact.put("this", new LinkedHashSet<>(ctx.sets.get(0)));
        int ci = 1;
        for (String[] p : mi.params) {
            if (st.isClassType(p[0])) {
                fact.put(p[1], ci < ctx.sets.size() ? new LinkedHashSet<>(ctx.sets.get(ci)) : new LinkedHashSet<>());
                ci++;
            }
        }
        return fact;
    }

    private void analyzeSummary(Summary s) {
        SymbolTable.MethodInfo savedMi = curMi;
        Summary savedS = curSummary;
        try {
            SymbolTable.MethodInfo mi = methodOf(s.methodId);
            if (mi == null) return;
            curMi = mi;
            curSummary = s;
            Map<String, Set<String>> fact = initialFact(mi, s.ctx);
            Set<String> ret = Collections.emptySet();
            if (mi.ast == null) {
                exprPts(st.mainAst.f14.f2, fact, s);
            } else {
                for (Node stmt : mi.ast.f8.nodes) handleStatement(stmt, fact, s);
                if (st.isClassType(mi.returnType)) ret = exprPts(mi.ast.f10, fact, s);
            }
            if (s.returnPts.addAll(ret)) refireDeps(s);
        } finally {
            curMi = savedMi;
            curSummary = savedS;
        }
    }

    private void handleStatement(Node stmt, Map<String, Set<String>> fact, Summary s) {
        if (stmt instanceof Statement st0) {
            handleStatement(st0.f0.choice, fact, s);
        } else if (stmt instanceof Block b) {
            for (Node x : b.f1.nodes) handleStatement(x, fact, s);
        } else if (stmt instanceof AssignmentStatement a) {
            storeToLhs(Ast.name(a.f0), exprPts(a.f2, fact, s), fact);
        } else if (stmt instanceof FieldAssignmentStatement f) {
            Set<String> rhs = exprPts(f.f4, fact, s);
            for (String site : varPts(Ast.name(f.f0), fact)) heapAdd(site, Ast.name(f.f2), rhs);
        } else if (stmt instanceof IfStatement is) {
            Statement t, e = null;
            if (is.f0.choice instanceof IfthenElseStatement ie) { t = ie.f4; e = ie.f6; }
            else t = ((IfthenStatement) is.f0.choice).f4;
            Map<String, Set<String>> a = copy(fact), b = copy(fact);
            handleStatement(t, a, s);
            if (e != null) handleStatement(e, b, s);
            replaceWith(fact, a);
            joinInto(fact, b);
        } else if (stmt instanceof WhileStatement w) {
            while (true) {
                Map<String, Set<String>> body = copy(fact);
                handleStatement(w.f4, body, s);
                if (!joinInto(fact, body)) break;
            }
        }
    }

    /** Field writes go to the heap; a null pts means not a reference. */
    private void storeToLhs(String lhs, Set<String> pts, Map<String, Set<String>> fact) {
        if (isField(lhs)) {
            for (String site : fact.getOrDefault("this", Set.of())) heapAdd(site, lhs, pts);
            return;
        }
        if (pts != null) fact.put(lhs, new LinkedHashSet<>(pts));
        else fact.remove(lhs);
    }

    /** Pts of a variable written as a bare identifier: local, or field of this. */
    private Set<String> varPts(String name, Map<String, Set<String>> fact) {
        if (name.equals("this") || isLocal(name)) return fact.getOrDefault(name, Set.of());
        if (isField(name)) {
            Set<String> r = new LinkedHashSet<>();
            for (String site : fact.getOrDefault("this", Set.of())) r.addAll(heapGet(site, name));
            return r;
        }
        return fact.getOrDefault(name, Set.of());
    }

    /** Pts of an expression; null if it is not a reference value. */
    private Set<String> exprPts(Expression e, Map<String, Set<String>> fact, Summary s) {
        Node c = e.f0.choice;
        if (c instanceof PrimaryExpression p) return primaryPts(p, fact, s);
        if (c instanceof FieldRead f) {
            Set<String> r = new LinkedHashSet<>();
            for (String site : varPts(Ast.name(f.f0), fact)) r.addAll(heapGet(site, Ast.name(f.f2)));
            return r;
        }
        if (c instanceof MessageSend ms) return analyzeCall(ms, fact, s);
        for (Node sub : childExpressions(c)) {
            if (sub instanceof Expression x) exprPts(x, fact, s);
            else primaryPts((PrimaryExpression) sub, fact, s);
        }
        return null;
    }

    private Set<String> primaryPts(PrimaryExpression p, Map<String, Set<String>> fact, Summary s) {
        Node c = p.f0.choice;
        if (c instanceof Identifier id) {
            String v = Ast.name(id);
            if (isLocal(v) && !fact.containsKey(v) && !st.isClassType(curMi.varType(v))) return null;
            return varPts(v, fact);
        }
        if (c instanceof ThisExpression) return fact.getOrDefault("this", Set.of());
        if (c instanceof AllocationExpression a) {
            String site = allocId.get(a);
            return site == null ? Set.of() : Set.of(site);
        }
        if (c instanceof BracketExpression b) return exprPts(b.f1, fact, s);
        if (c instanceof ArrayAllocationExpression a) { exprPts(a.f3, fact, s); return null; }
        if (c instanceof NotExpression n) { exprPts(n.f1, fact, s); return null; }
        return null;
    }

    private static List<Node> childExpressions(Node c) {
        if (c instanceof AndExpression x) return List.of(x.f0, x.f2);
        if (c instanceof OrExpression x) return List.of(x.f0, x.f2);
        if (c instanceof CompareExpression x) return List.of(x.f0, x.f2);
        if (c instanceof neqExpression x) return List.of(x.f0, x.f2);
        if (c instanceof AddExpression x) return List.of(x.f0, x.f2);
        if (c instanceof MinusExpression x) return List.of(x.f0, x.f2);
        if (c instanceof TimesExpression x) return List.of(x.f0, x.f2);
        if (c instanceof DivExpression x) return List.of(x.f0, x.f2);
        if (c instanceof ArrayLookup x) return List.of(x.f0, x.f2);
        if (c instanceof ArrayLength x) return List.of(x.f0);
        return List.of();
    }

    private Set<String> analyzeCall(MessageSend ms, Map<String, Set<String>> fact, Summary s) {
        CallGraph.CallSite cs = cg.sites.get(ms);
        Set<String> recvPts = primaryPts(ms.f0, fact, s);
        if (recvPts == null) recvPts = Set.of();
        recvPtsAtSite.computeIfAbsent(ms, k -> new LinkedHashSet<>()).addAll(recvPts);

        List<Set<String>> argPts = new ArrayList<>();
        if (cs != null) for (Expression a : cs.args) argPts.add(exprPts(a, fact, s));

        Set<String> retUnion = new LinkedHashSet<>();
        boolean anyRefReturn = false;
        for (String site : recvPts) {
            String klass = allocClass.get(site);
            SymbolTable.MethodInfo callee = klass == null ? null : st.resolveVirtual(klass, Ast.name(ms.f2));
            if (callee == null) continue;
            if (st.isClassType(callee.returnType)) anyRefReturn = true;

            List<Set<String>> ctx = new ArrayList<>();
            ctx.add(Set.of(site));
            for (int i = 0; i < callee.params.size(); i++) {
                if (st.isClassType(callee.params.get(i)[0])) {
                    Set<String> a = i < argPts.size() ? argPts.get(i) : null;
                    ctx.add(a == null ? Set.of() : a);
                }
            }
            Summary calleeSum = summaryOf(callee.id, new Context(ctx));
            addDep(s, calleeSum);
            retUnion.addAll(calleeSum.returnPts);
        }
        return anyRefReturn ? retUnion : null;
    }

    private static Map<String, Set<String>> copy(Map<String, Set<String>> f) {
        Map<String, Set<String>> c = new HashMap<>();
        f.forEach((k, v) -> c.put(k, new LinkedHashSet<>(v)));
        return c;
    }

    /** dst := dst U src; true iff dst grew. */
    private static boolean joinInto(Map<String, Set<String>> dst, Map<String, Set<String>> src) {
        boolean changed = false;
        for (Map.Entry<String, Set<String>> e : src.entrySet()) {
            Set<String> d = dst.get(e.getKey());
            if (d == null) {
                dst.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
                changed = changed || !e.getValue().isEmpty();
            } else if (d.addAll(e.getValue())) {
                changed = true;
            }
        }
        return changed;
    }

    private static void replaceWith(Map<String, Set<String>> dst, Map<String, Set<String>> src) {
        dst.clear();
        src.forEach((k, v) -> dst.put(k, new LinkedHashSet<>(v)));
    }
}
