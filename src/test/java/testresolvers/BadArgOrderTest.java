package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class BadArgOrderTest {
    public String fetchField(int arg, DataFetchingEnvironment env) {
        return "";
    }
}
