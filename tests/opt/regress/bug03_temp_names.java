// Bug 3 (hw2): temps were named TEMPn / tn without checking the program's own names.
class Bug03 {
    public static void main(String[] a) {
        System.out.println(new Names().run(2, 3));
    }
}

class Names {
    public int run(int t0, int t1) {
        int t2;
        int t3;
        int TEMP0;
        t2 = (t0 + t1) * (t0 - t1);
        t3 = ((t2 * t2) + (t0 * t1)) - (t1 * (t2 + t0));
        TEMP0 = (t3 + t2) + (t1 * (t0 + (t2 * t3)));
        return (TEMP0 * 2) + (t3 - t2);
    }
}
