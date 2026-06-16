// Bug 8 (hw4): a summary that read a heap cell was not re-analyzed when the
// cell grew later. useItem() is first analyzed seeing only an A in h.item, so
// its call it.foo() looked monomorphic and A.foo got inlined; at run time the
// second call sees a B. (useItem is recursive so that it is not itself inlined.)
// mjc-test: contains-O2 .foo()
class Bug08 {
    public static void main(String[] a) {
        System.out.println(new Driver().go());
    }
}

class Holder {
    A item;
    public int put(A x) {
        item = x;
        return 0;
    }
    public A get() {
        return item;
    }
}

class A {
    public int foo() { return 1; }
}

class B extends A {
    public int foo() { return 2; }
}

class Driver {
    Holder h;
    int depth;
    public int useItem(int n) {
        A it;
        int r;
        int d;
        it = h.get();
        r = it.foo();
        if (1 <= n) {
            d = this.useItem(n - 1);
            r = r + d;
        }
        return r;
    }
    public int fill() {
        int d;
        d = h.put(new B());
        return 0;
    }
    public int go() {
        int d;
        int r1;
        int r2;
        h = new Holder();
        d = h.put(new A());
        r1 = this.useItem(depth);
        d = this.fill();
        r2 = this.useItem(depth);
        return (r1 * 10) + r2;
    }
}
