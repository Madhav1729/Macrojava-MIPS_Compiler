// Bug 9 (hw4): a field write "value = v;" in an inlined body was left
// unqualified, so it wrote the caller's own field of the same name.
// mjc-test: absent-O2 c.set(
class Bug09 {
    public static void main(String[] a) {
        System.out.println(new User().run());
    }
}

class Cell {
    int value;
    public int set(int v) {
        value = v;
        return 0;
    }
    public int get() {
        return value;
    }
}

class User {
    int value;
    public int run() {
        Cell c;
        int d;
        int x;
        value = 1;
        c = new Cell();
        d = c.set(5);
        x = c.get();
        return (x * 100) + value;
    }
}
