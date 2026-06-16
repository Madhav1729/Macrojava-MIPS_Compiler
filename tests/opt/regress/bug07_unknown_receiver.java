// Bug 7 (hw3): a call whose receiver type is unknown (here: inside a lambda)
// did not keep its target alive, so the method was deleted although called.
// mjc-test: contains-O1 public int helper
import java.util.function.Function;

class Bug07 {
    public static void main(String[] a) {
        System.out.println(new Wrap().go(4));
    }
}

class Wrap {
    public int helper(int v) {
        return v * 10;
    }
    public int go(int n) {
        Function<Integer,Integer> f;
        f = (x) -> this.helper(x);
        return f.apply(n);
    }
}
