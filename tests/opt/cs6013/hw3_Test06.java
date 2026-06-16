// mjc-test: compile-only
// (CS6013 hw3 Test06: fubar(0) recurses forever; the test checks the analysis, not a run)
class Test06 {
    public static void main(String[] a) {
        System.out.println(new Test06Body().run());
    }
}

class Test06Body {
    public int run() {

        A object;
        int a;
        object = new A();
        a = 0;
        a = object.fubar(a);
        System.out.println(a);
    
        return 0;
    }
}

class A {
    public int fubar(int x) {
        int zero;
        A _this;
        int a;
        boolean b;
        _this = this;
        zero = 0;
        b = zero <= (x - 1);
        if (b) {
            a = 0;
        } else {
            a = _this.fubar(x);
        }
        return a;
    }

}
