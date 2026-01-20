// expect: error: type error
class Main {
    public static void main(String[] a) {
        System.out.println(new A().f());
    }
}

class A {
    public int f() {
        boolean b;
        b = 5;
        return 1;
    }
}
