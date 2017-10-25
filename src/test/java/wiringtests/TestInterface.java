package wiringtests;

import graphql.schema.DataFetchingEnvironment;

public interface TestInterface {
    String fetchField1(DataFetchingEnvironment env);
}
