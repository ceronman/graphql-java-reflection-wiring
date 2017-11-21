package testresolvers;

public class Product {
    private String name;
    private int id;

    public Product(int id) {
        name = String.format("Product #%d", id);
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }
}
