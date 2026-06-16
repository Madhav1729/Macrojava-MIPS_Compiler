class Test02 {
    public static void main(String[] a) {
        System.out.println(new Test02Body().run());
    }
}

class Test02Body {
    public int run() {
        int dummy_int;

        FooInator fooer;
        A object;
        fooer = new FooInator();
        object = new A();
        
        dummy_int = fooer.fooIfy(object);
        object = new B();
        
        dummy_int = fooer.fooIfy(object);
        object = new C();
        
        dummy_int = fooer.fooIfy(object);
    
        return 0;
    }
}

class A {
    public int foo() {
        return 1;
    }

}

class B extends A {
    public int foo() {
        return 2;
    }

}

class C extends B {
    public int foo() {
        return 3;
    }

}

class FooInator {
    public int fooIfy(A object) {
        int z;
        
        z = object.foo();
        System.out.println(z);
        return z;
    }

}
