// Bug 10 (hw4): a field used as a receiver or array in an inlined body
// (items[i], inner.get()) was left unqualified and named the caller's field.
// mjc-test: absent-O2 b.first(
class Bug10 {
    public static void main(String[] a) {
        System.out.println(new Caller().run());
    }
}

class Leaf {
    int v;
    public int init(int x) { v = x; return 0; }
    public int get() { return v; }
}

class Box {
    int[] items;
    Leaf inner;
    public int fill() {
        items = new int[3];
        items[0] = 7;
        inner = new Leaf();
        return inner.init(30);
    }
    public int first() {
        int r;
        r = items[0];
        return r + (inner.get());
    }
}

class Caller {
    int[] items;
    Leaf inner;
    public int run() {
        Box b;
        int d;
        items = new int[3];
        items[0] = 1000;
        inner = new Leaf();
        d = inner.init(5000);
        b = new Box();
        d = b.fill();
        return b.first();
    }
}
