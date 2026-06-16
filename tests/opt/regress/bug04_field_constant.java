// Bug 4 (hw3): fields were tracked like locals, so a field "constant" survived
// a call that changes it, and an unassigned field (TOP) merged into a constant.
class Bug04 {
    public static void main(String[] a) {
        System.out.println(new Holder().run());
    }
}

class Holder {
    int f;
    boolean ready;
    public boolean flag() {
        return ready;                  // false at run time, unknown to the analysis
    }
    public int bump() {
        f = 7;
        return 0;
    }
    public int pick(boolean c) {
        int x;
        if (c) x = 5;
        else x = f;
        return x;
    }
    public int run() {
        int y;
        int d;
        int z;
        boolean c;
        f = 5;
        d = this.bump();
        y = f;
        c = this.flag();
        z = this.pick(c);
        return (y * 100) + z;
    }
}
