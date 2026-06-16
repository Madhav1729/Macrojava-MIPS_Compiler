class Test11 {
    public static void main(String[] a) {
        System.out.println(new Test11Body().run());
    }
}

class Test11Body {
    public int run() {
        int dummy_int;

        A object;
        int x;
        object = new A();
        dummy_int = object.init();
        
        x = object.foo();
        System.out.println(x);
        object = new B();
        dummy_int = object.init();
        
        x = object.foo();
        System.out.println(x);
        object = new C();
        dummy_int = object.init();
        
        x = object.foo();
        System.out.println(x);
    
        return 0;
    }
}

class A {
    int x;

    public int init() {
        int dummy_int;
        A _this;
        _this = this;
        dummy_int = _this.set_x(100);
        return 0;
    }

    public int foo() {
        int dummy_int;
        A _this;
        int temp;
        _this = this;
        temp = _this.get_x();
        temp = temp + 1;
        dummy_int = _this.set_x(temp);
        return temp;
    }

    public int get_x() { return x; }
    public int set_x(int nv) { x = nv; return 0; }
}

class B extends A {
    int x;

    public int init() {
        int dummy_int;
        A _this;
        _this = this;
        dummy_int = _this.set_x(200);
        return 0;
    }

    public int foo() {
        int dummy_int;
        A _this;
        int temp;
        _this = this;
        temp = _this.get_x();
        temp = temp + 1;
        dummy_int = _this.set_x(temp);
        return temp;
    }

    public int get_x() { return x; }
    public int set_x(int nv) { x = nv; return 0; }
}

class C extends A {
    public int foo() {
        int dummy_int;
        A _this;
        int temp;
        _this = this;
        temp = _this.get_x();
        temp = temp + 1;
        dummy_int = _this.set_x(temp);
        return temp;
    }

}
