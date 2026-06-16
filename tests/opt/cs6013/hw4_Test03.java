class Test03 {
    public static void main(String[] a) {
        System.out.println(new Test03Body().run());
    }
}

class Test03Body {
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
    public int foo(int x) {
        boolean b;
        int y;
        b = x <= (1 - 1);
        if (b)
            y = 1;
        else{
            x = x - 1;
            
            y = this.foo(x);
            x = x + 1;
            y = y * x;
        }
        return y;
    }

}
