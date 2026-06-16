class Test05 {
    public static void main(String[] a) {
        System.out.println(new Test05Body().run());
    }
}

class Test05Body {
    public int run() {

        A object;
        BoolInator bool;
        int x;
        boolean b;
        bool = new BoolInator();
        b = bool.boolIfy();
        if (b)
            object = new A();
        else
            object = new B();
        
        x = object.foo(5);
        System.out.println(x);
        
        x = object.bar(5);
        System.out.println(x);
    
        return 0;
    }
}

class A {
    public int foo(int x) {
        int y;
        y = x * x;
        return y;
    }

    public int bar(int x) {
        int y;
        y = x + x;
        return y;
    }

}

class B extends A {
    public int foo(int x) {
        int y;
        y = x * x;
        return y;
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
