class Test09 {
    public static void main(String[] a) {
        System.out.println(new Test09Body().run());
    }
}

class Test09Body {
    public int run() {

        A object;
        int a;
        int i;
        int j;
        int k;
        int l;
        boolean b;
        object = new A();
        a = 2;
        j = 10;
        k = 1;
        i = j;
        b = i <= (j - 1);
        while (b) {
            a = object.foo(a);
            i = i + k;
            b = i <= (j - 1);
        }
        System.out.println(a);
        a = 3;
        k = 11;
        i = j;
        b = i <= (k - 1);
        while (b) {
            a = object.bar(a);
            i = i + k;
            b = i <= (k - 1);
        }
        System.out.println(a);
        a = 4;
        j = 0;
        k = 10;
        l = 1;
        i = j;
        b = i <= (k - 1);
        while (b) {
            a = object.fubar(a, k);
            i = i + l;
            b = i <= (k - 1);
        }
        System.out.println(a);
    
        return 0;
    }
}

class A {
    public int foo(int x) {
        int z;
        z = 111;
        System.out.println(z);
        z = x * x;
        return z;
    }

    public int bar(int y) {
        int z;
        z = 222;
        System.out.println(z);
        z = y + y;
        return z;
    }

    public int fubar(int x, int y) {
        int z;
        z = 333;
        System.out.println(z);
        z = x + y;
        z = z * x;
        z = z * y;
        return z;
    }

}
