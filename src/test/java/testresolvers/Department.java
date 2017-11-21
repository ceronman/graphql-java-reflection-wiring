package testresolvers;

import graphql.schema.DataFetchingEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Department {
    private String name;
    private int id;

    public Department(Shop shop, int id) {
        name = String.format("Department #%d", id);
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public List<Product> fetchProducts(DataFetchingEnvironment env) {
        AtomicInteger callCounter = env.getContext();
        callCounter.incrementAndGet();
        return Arrays.asList(
                new Product(id * 10000 + 1),
                new Product(id * 10000 + 2));
    }
}
