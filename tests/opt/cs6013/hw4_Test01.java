class Test01 {
    public static void main(String[] a) {
        System.out.println(new Test01Body().run());
    }
}

class Test01Body {
    public int run() {

        A object;
        int x;
        object = new A();
        
        x = object.foo(5);
        System.out.println(x);
        
        x = object.foo(x);
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
        
        y = this.foo(x);
        return y;
    }

}
