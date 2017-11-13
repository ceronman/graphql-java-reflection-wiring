package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class ArgsMismatchTest {
    public Integer fetchMissingArg(DataFetchingEnvironment env, int a, String b) {
        return 1;
    }
    public Integer fetchExtraArg(DataFetchingEnvironment env, int a, String b) {
        return 1;
    }
    public Integer fetchWrongArgs(DataFetchingEnvironment env, int a, String b) {
        return 1;
    }
}
