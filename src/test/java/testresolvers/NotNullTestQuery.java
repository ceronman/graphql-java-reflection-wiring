package testresolvers;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;

public class NotNullTestQuery {
    public int getField1() { return 1; }
    public TypeA getField2() { return new TypeA(); }
    public List<String> getField3() { return new ArrayList<>(); }
    public List<Double> getField4() { return new ArrayList<>(); }
    public List<TypeA> getField5() { return new ArrayList<>(); }
    public String fetchField6(DataFetchingEnvironment env, String arg) {
        return arg;
    }
}
