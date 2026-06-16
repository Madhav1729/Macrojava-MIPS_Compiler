class Test08 {
    public static void main(String[] a) {
        System.out.println(new Test08Body().run());
    }
}

class Test08Body {
    public int run() {

        A object;
        int a;
        object = new A();
        a = 5;
        a = object.foo(a);
        System.out.println(a);
    
        return 0;
    }
}

class A {
    public int foo(int x) {
        boolean b;
        int y;
        int one;
        b = true;
        one = 1;
        y = x;
        while (b) {
            y = y - one;
            b = false;
        }
        return y;
    }

}
