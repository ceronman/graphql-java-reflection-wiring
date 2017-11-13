package testresolvers;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BadListTest {
    public Stream<Integer> fetchStreamField(DataFetchingEnvironment env) {
        return Stream.of(1, 2, 3);
    }
    public List fetchListField(DataFetchingEnvironment env) {
        return new ArrayList();
    }
}
