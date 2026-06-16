class Test01 {
    public static void main(String[] a) {
        System.out.println(new Test01Body().run());
    }
}

class Test01Body {
    public int run() {

        A object;
        int a;
        int b;
        int c;
        object = new A();
        a = 2;
        b = 3;
        a = object.foo(a);
        b = object.bar(b);
        c = 10;
        c = c * a;
        c = c + b;
        System.out.println(c);
    
        return 0;
    }
}

class A {
    public int foo(int x) {
        int z;
        z = x * x;
        return z;
    }

    public int bar(int y) {
        int z;
        z = y + y;
        return z;
    }

}

class B extends A {
    public int foo(int x) {
        A a;
        int z;
        a = new A();
        z = a.foo(z);
        return z;
    }

    public int bar(int y) {
        int z;
        z = y * y;
        return z;
    }

}
