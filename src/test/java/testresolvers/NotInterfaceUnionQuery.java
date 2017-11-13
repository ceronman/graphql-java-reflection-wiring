package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class NotInterfaceUnionQuery {
    public NotInterfaceUnion fetchUnionFieldA(DataFetchingEnvironment env) { return null; }
    public NotInterfaceUnion fetchUnionFieldB(DataFetchingEnvironment env) { return null; }
}
