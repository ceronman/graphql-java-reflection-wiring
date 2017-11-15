package testresolvers;

public class ClassWithoutDefaultConstructor {
    private int i;
    public ClassWithoutDefaultConstructor(int i) {
        this.i = i;
    }
    public int getField() { return i; }
}
