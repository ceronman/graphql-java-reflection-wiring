package uniontests;

import graphql.schema.DataFetchingEnvironment;

public class BadUnionTestQuery {
    public static InterfaceWithMethodsUnion fetchUnionFieldA(DataFetchingEnvironment env) {
        return new BadTypeA();
    }

    public static InterfaceWithMethodsUnion fetchUnionFieldB(DataFetchingEnvironment env) {
        return new BadTypeB();
    }
}
