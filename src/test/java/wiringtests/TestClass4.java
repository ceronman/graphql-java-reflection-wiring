package wiringtests;

import graphql.schema.DataFetchingEnvironment;

public class TestClass4 {
    public String getField1() {
        return "result_field_1";
    }

    public int fetchField2(DataFetchingEnvironment env) {
        return 42;
    }
}
