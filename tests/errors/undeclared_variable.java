// expect: symbol not found
class Main {
    public static void main(String[] a) {
        System.out.println(new A().f());
    }
}

class A {
    public int f() {
        x = 5;
        return x;
    }
}
