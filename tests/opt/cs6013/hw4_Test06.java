class Test06 {
    public static void main(String[] a) {
        System.out.println(new Test06Body().run());
    }
}

class Test06Body {
    public int run() {
        int dummy_int;

        New gen;
        A a1;
        A a2;
        A a3;
        A tmp;
        int x;
        gen = new New();
        a1 = gen.newA();
        a2 = gen.newA();
        tmp = new A();
        dummy_int = a1.set_a(tmp);
        tmp = new B();
        dummy_int = a2.set_a(tmp);
        a3 = a1.get_a();
        
        x = a3.foo();
        System.out.println(x);
    
        return 0;
    }
}

class New {
    public A newA() {
        A a;
        a = new A();
        return a;
    }

}

class A {
    A a;
    public int foo() {
        return 1;
    }

    public A get_a() { return a; }
    public int set_a(A nv) { a = nv; return 0; }
}

class B extends A {
    public int foo() {
        return 2;
    }

}

class BoolInator {
    boolean b;

    public boolean boolIfy() {
        int dummy_int;
        boolean b;
        BoolInator _this;
        _this = this;
        b = _this.get_b();
        b = !b;
        dummy_int = _this.set_b(b);
        b = _this.get_b();
        return b;
    }

    public boolean get_b() { return b; }
    public int set_b(boolean nv) { b = nv; return 0; }
}
