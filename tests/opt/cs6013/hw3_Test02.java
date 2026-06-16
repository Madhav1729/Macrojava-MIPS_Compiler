class Test02 {
    public static void main(String[] a) {
        System.out.println(new Test02Body().run());
    }
}

class Test02Body {
    public int run() {

        A object;
        int a;
        int i;
        int j;
        int k;
        int l;
        object = new A();
        a = 2;
        j = 10;
        k = 1;
        i = j;
while (i <= (j - 1)) {
            a = object.foo(a);
        
i = i + k;
}
        System.out.println(a);
        a = 3;
        k = 11;
        i = j;
while (i <= (k - 1)) {
            a = object.bar(a);
        
i = i + k;
}
        System.out.println(a);
        a = 4;
        j = 0;
        k = 10;
        l = 1;
        i = j;
while (i <= (k - 1)) {
            a = object.fubar(a, k);
        
i = i + l;
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
