package wiringtests;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestClass3 {
    public static String fetchSimplefield(DataFetchingEnvironment env) {
        return "response";
    }

    public static String fetchFieldwithargs(DataFetchingEnvironment env, int a, int b) {
        return String.format("%d, %d", a, b);
    }

    public static TestClass4 fetchTest4(DataFetchingEnvironment env) {
        return new TestClass4();
    }

    public static Boolean fetchBooleanScalar(DataFetchingEnvironment env) {
        return true;
    }

    public static boolean fetchBooleanPrimitiveScalar(DataFetchingEnvironment env) {
        return false;
    }

    public static Integer fetchIntegerScalar(DataFetchingEnvironment env) {
        return 1;
    }

    public static int fetchIntegerPrimitiveScalar(DataFetchingEnvironment env) {
        return 2;
    }

    public static Double fetchDoubleScalar(DataFetchingEnvironment env) {
        return 3.0;
    }

    public static double fetchDoublePrimitiveScalar(DataFetchingEnvironment env) {
        return 4.0;
    }

    public static Float fetchFloatScalar(DataFetchingEnvironment env) {
        return 5.0f;
    }

    public static float fetchFloatPrimitiveScalar(DataFetchingEnvironment env) {
        return 6.0f;
    }

    public static String fetchStringScalar(DataFetchingEnvironment env) {
        return "string result";
    }

    public static String fetchIDScalar(DataFetchingEnvironment env) {
        return "ID result";
    }

    public static List<TestClass4> fetchListList(DataFetchingEnvironment env) {
        return Arrays.asList(new TestClass4());
    }

    public static ArrayList<Integer> fetchArrayList (DataFetchingEnvironment env) {
        return new ArrayList<>(Arrays.asList(3, 4));
    }

    public static String fetchFieldWithListParam(DataFetchingEnvironment env, List<Integer> arg) {
        return String.join("_", arg.stream().map(Object::toString).collect(Collectors.toList()));
    }

    public static String fetchObjectArg(DataFetchingEnvironment env, InputTestClass obj) {
        return obj.getField1() + obj.getField2();
    }

    public static String fetchEnumArg(DataFetchingEnvironment env, TestEnum e) {
        return e.toString();
    }

    public static TestEnum fetchEnumReturn(DataFetchingEnvironment env) {
        return TestEnum.ONE;
    }

    public static TestInterface fetchInterfaceField(DataFetchingEnvironment env) {
        return null;
    }

    public static TestClass6 fetchTestClass6(DataFetchingEnvironment env) {
        return null;
    }
}
