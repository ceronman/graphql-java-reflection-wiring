package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class EnumTestQuery {
    public String fetchField1(DataFetchingEnvironment env, TestEnum e) {
        return e.toString();
    }
    public TestEnum getField2() {
        return TestEnum.ONE;
    }
}
