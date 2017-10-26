package wiringtests;

import graphql.schema.DataFetchingEnvironment;

public class TestClass6 {
    public String fetchField1(DataFetchingEnvironment env) {
        return "result_field_1";
    }
    public int fetchField2(DataFetchingEnvironment env) {
        return 42;
    }
}
