package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class ObjectTestQuery {
    public TypeA getFieldA() {
        return new TypeA();
    }
    public String fetchFieldB(DataFetchingEnvironment env, InputTypeA obj) {
        return obj.getField1() + obj.getField2();
    }
}
