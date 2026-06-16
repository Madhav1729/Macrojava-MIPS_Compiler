// Bug 5 (hw3): the return value was rewritten from the state before a trailing if.
class Bug05 {
    public static void main(String[] a) {
        System.out.println(new Ret().run(true));
    }
}

class Ret {
    public int run(boolean c) {
        int x;
        x = 1;
        if (c) x = 2;
        else {
        }
        return x;
    }
}
