// Bug 6 (hw3): "f = e;" inside a class looked like a dead local assignment, and
// methods that only write fields looked pure, so their calls were deleted.
// mjc-test: contains-O1 this.inc()
class Bug06 {
    public static void main(String[] a) {
        System.out.println(new Counter().run());
    }
}

class Counter {
    int count;
    int last;
    public int inc() {
        count = count + 1;
        return 0;
    }
    public int run() {
        int d;
        d = this.inc();
        d = this.inc();
        d = this.inc();
        last = 42;
        return (count * 100) + last;
    }
}
