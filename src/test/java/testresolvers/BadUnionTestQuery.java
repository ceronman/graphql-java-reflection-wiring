package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class BadUnionTestQuery {
    public InterfaceWithMethodsUnion fetchUnionFieldA(DataFetchingEnvironment env) { return null; }
    public InterfaceWithMethodsUnion fetchUnionFieldB(DataFetchingEnvironment env) { return null; }
}
