package testresolvers;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ListTestQuery {
    public ArrayList<Integer> fetchField1(DataFetchingEnvironment env) {
        return new ArrayList<>(Arrays.asList(3, 4));
    }

    public List<TypeA> fetchField2(DataFetchingEnvironment env) {
        return Arrays.asList(new TypeA());
    }

    public String fetchField3(DataFetchingEnvironment env, List<Integer> arg) {
        return String.join("_", arg.stream().map(Object::toString).collect(Collectors.toList()));
    }
}
