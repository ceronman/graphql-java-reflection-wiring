package wiringtests;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class TestClass1 {
    public int getIntField() {
        return 0;
    }

    public float fetchFloatField(DataFetchingEnvironment env) {
        return 1.0f;
    }

    public boolean fetchNoEnv() {
        return false;
    }

    public String fetchBadOrder(int arg, DataFetchingEnvironment env) {
        return "";
    }

    public Integer fetchManyArguments(DataFetchingEnvironment env, int a, String b) {
        return 1;
    }

    private Double getSomeField() {
        return 1.0;
    }

    protected Boolean fetchField1(DataFetchingEnvironment env) {
        return true;
    }

    public int fetchField2(DataFetchingEnvironment env) {
        return 0;
    }

    public int fetchField2(DataFetchingEnvironment env, String option) {
        return 1;
    }

    public int isField3() {
        return 0;
    }

    public boolean isField4() {
        return true;
    }

    public Stream<Integer> fetchStreamField(DataFetchingEnvironment env) {
        return Stream.of(1, 2, 3);
    }

    public List fetchListField(DataFetchingEnvironment env) {
        return new ArrayList();
    }
}
