class Test08 {
    public static void main(String[] a) {
        System.out.println(new Test08Body().run());
    }
}

class Test08Body {
    public int run() {

        Driver driver;
        A a;
        B b;
        int x;
        driver = new Driver();
        a = new A();
        b = new B();
        
        x = driver.foo(a, 5);
        System.out.println(x);
        
        x = driver.foo(b, 5);
        System.out.println(x);
    
        return 0;
    }
}

class Driver {
    public int foo(A object, int x) {
        int z;
        
        z = object.bar(x);
        return z;
    }

}

class A {
    public int bar(int x) {
        int y;
        y = x + x;
        return y;
    }

}

class B extends A {
    public int bar(int x) {
        int y;
        y = x * x;
        return y;
    }

}
