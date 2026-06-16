class Test12 {
    public static void main(String[] a) {
        System.out.println(new Test12Body().run());
    }
}

class Test12Body {
    public int run() {
        int dummy_int;

        Container container;
        Item item;
        container = new Container();
        item = new Item();
        dummy_int = container.set_item1(item);
        dummy_int = container.recurse(3);
        item = container.get_item3();
        
        dummy_int = item.execute();
    
        return 0;
    }
}

class Container {
    Item item1;
    Item item2;
    Item item3;

    public int recurse(int x) {
        int dummy_int;
        Container _this;
        boolean base;
        int retval;
        Item temp;
        _this = this;
        base = x <= (1 - 1);
        if (base) {
            retval = 456;
        } else {
            x = x - 1;
            retval = _this.recurse(x);
            temp = _this.get_item2();
            dummy_int = _this.set_item3(temp);
            temp = _this.get_item1();
            dummy_int = _this.set_item2(temp);
        }
        return retval;
    }

    public Item get_item1() { return item1; }
    public int set_item1(Item nv) { item1 = nv; return 0; }
    public Item get_item2() { return item2; }
    public int set_item2(Item nv) { item2 = nv; return 0; }
    public Item get_item3() { return item3; }
    public int set_item3(Item nv) { item3 = nv; return 0; }
}

class Item {
    public int execute() {
        System.out.println(123);
        return 0;
    }

}
