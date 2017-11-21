package testresolvers;

import java.util.Arrays;
import java.util.List;

public class BatchLoaderTest {
    public static List<Shop> getShops() {
        return Arrays.asList(new Shop(1), new Shop(2));
    }
}
