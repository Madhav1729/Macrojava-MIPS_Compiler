class Test07 {
    public static void main(String[] a) {
        System.out.println(new Test07Body().run());
    }
}

class Test07Body {
    public int run() {

        A object;
        int x;
        object = new B();
        
        x = object.foo();
        System.out.println(x);
        
        x = object.bar();
        System.out.println(x);
    
        return 0;
    }
}

class A {
    public int foo() {
        return 0;
    }

    public int bar() {
        int x;
        
        x = this.foo();
        x = x * x;
        return x;
    }

}

class B extends A {
    int x;
    public int foo() {
        int dummy_int;
        int temp;
        B _this;
        _this = this;
        temp = _this.get_x();
        temp = temp + 1;
        dummy_int = _this.set_x(temp);
        return temp;
    }

    public int get_x() { return x; }
    public int set_x(int nv) { x = nv; return 0; }
}
