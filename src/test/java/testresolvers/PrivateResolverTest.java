package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class PrivateResolverTest {
    private float getField1() { return 0.0f; }
    private int fetchField2(DataFetchingEnvironment env) { return 0; }
}
