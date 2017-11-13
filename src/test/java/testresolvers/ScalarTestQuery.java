package testresolvers;

import graphql.schema.DataFetchingEnvironment;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScalarTestQuery {
    public Boolean fetchBooleanScalar(DataFetchingEnvironment env) {
        return true;
    }

    public boolean fetchBooleanPrimitiveScalar(DataFetchingEnvironment env) {
        return false;
    }

    public Integer fetchIntegerScalar(DataFetchingEnvironment env) {
        return 1;
    }

    public int fetchIntegerPrimitiveScalar(DataFetchingEnvironment env) {
        return 2;
    }

    public Double fetchDoubleScalar(DataFetchingEnvironment env) {
        return 3.0;
    }

    public double fetchDoublePrimitiveScalar(DataFetchingEnvironment env) {
        return 4.0;
    }

    public Float fetchFloatScalar(DataFetchingEnvironment env) {
        return 5.0f;
    }

    public float fetchFloatPrimitiveScalar(DataFetchingEnvironment env) {
        return 6.0f;
    }

    public String fetchStringScalar(DataFetchingEnvironment env) {
        return "string result";
    }

    public String fetchIDScalar(DataFetchingEnvironment env) {
        return "ID result";
    }

    public String fetchField(DataFetchingEnvironment env,
                             boolean boolArg, Boolean boolArg2,
                             int intArg, Integer intArg2,
                             double doubleArg, Double doubleArg2,
                             float floatArg, Float floatArg2,
                             String strArg, String idArg) {

        return Stream.of(boolArg, boolArg2, intArg, intArg2, doubleArg, doubleArg2, floatArg,
                floatArg2, strArg, idArg).map(String::valueOf).collect(Collectors.joining("_"));
    }
}
