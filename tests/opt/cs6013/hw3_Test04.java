class Test04 {
    public static void main(String[] a) {
        System.out.println(new Test04Body().run());
    }
}

class Test04Body {
    public int run() {

        A object;
        int a;
        int two;
        int three;
        two = 2;
        three = 3;
        object = new A();
        a = object.foo(two, three);
        System.out.println(a);
        a = object.bar(two, three);
        System.out.println(a);
    
        return 0;
    }
}

class A {
    boolean b;

    public int foo(int x, int y) {
        int dummy_int;
        int p;
        int q;
        int r;
        boolean s;
        A _this;
        s = true;
        _this = this;
        dummy_int = _this.set_b(s);
        s = _this.get_b();
        if (s) {
            p = x;
            q = y;
        } else {
            p = y;
            q = x;
        }
        r = p + q;
        return r;
    }

    public int bar(int x, int y) {
        int dummy_int;
        int p;
        int q;
        int r;
        boolean s;
        A _this;
        s = false;
        _this = this;
        dummy_int = _this.set_b(s);
        s = _this.get_b();
        if (s) {
            p = x;
            q = y;
            r = p + q;
        } else {
            p = y;
            q = x;
            r = p + q;
        }
        return r;
    }

    public boolean get_b() { return b; }
    public int set_b(boolean nv) { b = nv; return 0; }
}
