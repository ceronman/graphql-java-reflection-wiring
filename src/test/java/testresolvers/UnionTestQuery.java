package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class UnionTestQuery {
    public static TestUnion fetchUnionFieldA(DataFetchingEnvironment env) {
        return new TypeWithString();
    }

    public static TestUnion fetchUnionFieldB(DataFetchingEnvironment env) {
        return new TypeWithInt();
    }
}
