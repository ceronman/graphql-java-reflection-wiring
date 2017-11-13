package testresolvers;

import graphql.schema.DataFetchingEnvironment;

public class OverloadedMethodTest {
    public String getField1() { return ""; }
    public String getField1(int x) { return Integer.toString(x); }
    public int fetchField2(DataFetchingEnvironment env) { return 0; }
    public int fetchField2(DataFetchingEnvironment env, int x) { return 0; }
}
