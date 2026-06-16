class Test03 {
    public static void main(String[] a) {
        System.out.println(new Test03Body().run());
    }
}

class Test03Body {
    public int run() {

        A object;
        int a;
        boolean b;
        object = new A();
        b = object.choice();
        b = true;
        a = 10;
        if (b) {
        } else {
            a = 5;
        }
        System.out.println(a);
        b = object.choice();
        b = false;
        a = 100;
        if (b) {
        } else {
            a = 50;
        }
        System.out.println(a);
        b = object.choice();
        if (b) {
            b = object.choice();
            if (b) {
                a = 1000;
            } else {
                a = 1000;
            }
        } else {
            b = object.choice();
            if (b) {
                a = 1000;
            } else {
                a = 1000;
            }
        }
        System.out.println(a);
    
        return 0;
    }
}

class A {
    int ctr;

    public boolean choice() {
        int dummy_int;
        A _this;
        int x;
        int zero;
        int one;
        int three;
        int temp;
        boolean cond;
        _this = this;
        zero = 0;
        one = 1;
        three = 3;
        temp = _this.get_ctr();
        temp = temp + one;
        dummy_int = _this.set_ctr(temp);
        x = _this.get_ctr();
        cond = zero <= (x - 1);
        while (cond) {
            x = x - three;
            cond = zero <= (x - 1);
        }
        cond = x <= (zero - 1);
        cond = !cond;
        return cond;
    }

    public int get_ctr() { return ctr; }
    public int set_ctr(int nv) { ctr = nv; return 0; }
}
