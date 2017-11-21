package testresolvers;

import java.util.Arrays;
import java.util.List;

public class Shop {
    private String name;
    private int id;

    public Shop(int id) {
        this.name = String.format("Shop #%d", id);
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public List<Department> getDepartments() {
        return Arrays.asList(
                new Department(this, id * 100 + 1),
                new Department(this, id * 100 + 2));
    }
}
