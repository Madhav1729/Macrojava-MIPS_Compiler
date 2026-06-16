// Bug 2 (hw2): a loop with a literal condition produced "false = false;".
// mjc-test: absent-O1 x = 99
class Bug02 {
    public static void main(String[] a) {
        System.out.println(new Lit().run(3));
    }
}

class Lit {
    public int run(int n) {
        int x;
        x = n;
        while (false) {
            x = 99;
        }
        while ((x <= 5)) {
            x = x + 1;
        }
        return x;
    }
}
