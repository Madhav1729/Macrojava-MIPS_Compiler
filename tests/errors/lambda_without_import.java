// expect: lambdas need 'import java.util.function.Function;'
class Main {
    public static void main(String[] a) {
        System.out.println(new A().f(2));
    }
}

class A {
    public int f(int x) {
        Function<Integer,Integer> inc;
        inc = (y) -> y + 1;
        return inc.apply(x);
    }
}
