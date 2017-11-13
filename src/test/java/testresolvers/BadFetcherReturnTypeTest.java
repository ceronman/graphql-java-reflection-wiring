package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class BadFetcherReturnTypeTest {
    public float fetchField(DataFetchingEnvironment env) { return 1.0f; }
}
