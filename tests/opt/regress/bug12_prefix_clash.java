// Bug 12 (hw4): the inlining prefix _inlN_ was not checked against existing
// names; here the program already uses names starting with inl0_ and inl1_.
// mjc-test: absent-O2 p.twice(
class Bug12 {
    public static void main(String[] a) {
        System.out.println(new Host().run(3));
    }
}

class P {
    public int twice(int x) {
        int y;
        y = x + x;
        return y;
    }
}

class Host {
    public int run(int inl0_x) {
        int inl0_y;
        int inl1_x;
        P p;
        p = new P();
        inl0_y = 100;
        inl1_x = p.twice(inl0_x);
        return inl0_y + inl1_x;
    }
}
