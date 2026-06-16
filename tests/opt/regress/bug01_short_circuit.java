// Bug 1 (hw2): the right operand of && and || was evaluated before the branch.
// Here it prints, so evaluating it when the left side decides the result is visible.
class Bug01 {
    public static void main(String[] a) {
        System.out.println(new Probe().run(0));
    }
}

class Probe {
    int calls;
    public boolean noisy(int v) {
        calls = calls + 1;
        System.out.println(v);
        return true;
    }
    public int run(int x) {
        int r;
        r = 0;
        if ((1 <= x) && (this.noisy(111))) r = r + 1;
        if ((0 <= x) || (this.noisy(222))) r = r + 10;
        if ((0 <= x) && (this.noisy(333))) r = r + 100;
        return r + (calls * 1000);
    }
}
