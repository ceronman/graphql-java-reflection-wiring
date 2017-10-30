package uniontests;

import graphql.schema.DataFetchingEnvironment;

public class UnionTestQuery {
    public static TestUnion fetchUnionFieldA(DataFetchingEnvironment env) {
        return new TypeA();
    }

    public static TestUnion fetchUnionFieldB(DataFetchingEnvironment env) {
        return new TypeB();
    }
}
