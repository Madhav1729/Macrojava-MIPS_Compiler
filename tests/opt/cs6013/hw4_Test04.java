class Test04 {
    public static void main(String[] a) {
        System.out.println(new Test04Body().run());
    }
}

class Test04Body {
    public int run() {

        A object;
        int x;
        object = new A();
        
        x = object.foo(5);
        System.out.println(x);
    
        return 0;
    }
}

class A {
    int field;

    public int foo(int x) {
        int dummy_int;
        int y;
        int temp;
        A _this;
        _this = this;
        temp = _this.get_field();
        temp = temp + x;
        dummy_int = _this.set_field(temp);
        temp = _this.get_field();
        y = x * temp;
        return y;
    }

    public int get_field() { return field; }
    public int set_field(int nv) { field = nv; return 0; }
}
