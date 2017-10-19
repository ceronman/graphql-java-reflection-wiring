import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static org.junit.Assert.*;

public class ReflectionWiringFactoryTest {

    private ReflectionWiringFactory wireSchema(String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory("wiringtests", typeDefinitionRegistry);
        RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(wiringFactory).build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        return wiringFactory;
    }

    private String executeQuery(String schema, String query) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory("wiringtests", typeDefinitionRegistry);
        RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(wiringFactory).build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        for (String error : wiringFactory.getErrors()) {
            throw new RuntimeException(error);
        }
        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionResult executionResult = build.execute(query);
        for (GraphQLError error : executionResult.getErrors()) {
            throw new RuntimeException(error.toString());
        }
        return executionResult.getData().toString();
    }

    @Test
    public void missingClassError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: NonExistentClass\n" +
                "}\n" +
                "\n" +
                "type NonExistentClass {\n" +
                "    hello: String\n" +
                "}");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.NonExistentClass' not found", 
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void missingFieldError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    hello: String\n" +
                "}");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'hello' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badGetterReturnTypeError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    intField: String\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'getIntField' in class 'wiringtests.TestClass1' returns 'int' instead of expected 'TypeName{name='String'}'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badFetcherReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    floatField: Boolean\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchFloatField' in class 'wiringtests.TestClass1' returns 'float' instead of expected 'TypeName{name='Boolean'}'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badFetcherListReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    streamField: [Int]\n" +
                "    listField: [Int]\n" +
                "}");
        assertEquals(4, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchStreamField' in class 'wiringtests.TestClass1' returns " +
                        "'java.util.stream.Stream' instead of expected 'ListType{type=TypeName{name='Int'}}'",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Method 'fetchListField' in class 'wiringtests.TestClass1' returns " +
                        "'java.util.List' instead of expected 'ListType{type=TypeName{name='Int'}}'",
                wiringFactory.getErrors().get(2));
    }

    @Test
    public void fetcherWithoutEnvArgument() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    noEnv: Boolean\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchNoEnv' in class 'wiringtests.TestClass1' doesn't have DataFetchingEnvironment as first parameter",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherBadArgumentOrder() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    badOrder: String\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchBadOrder' in class 'wiringtests.TestClass1' doesn't have DataFetchingEnvironment as first parameter",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherMissingArguments() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    manyArguments(a: Int, b: String, c: Boolean): Int\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchManyArguments' in class 'wiringtests.TestClass1' doesn't have the right number of arguments",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherExtraArguments() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    manyArguments(a: Int): Int\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchManyArguments' in class 'wiringtests.TestClass1' doesn't have the right number of arguments",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherSignatureMismatch() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    manyArguments(a: String, b: Int): Int\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Type mismatch in method 'fetchManyArguments', argument '1' in class 'wiringtests.TestClass1' expected 'TypeName{name='String'}', got 'int'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void getterIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    someField: Float\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'getSomeField' in class 'wiringtests.TestClass1' is not public",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    field1: Boolean\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchField1' in class 'wiringtests.TestClass1' is not public",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherIsOverloaded() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    field2: Int\n" +
                "}");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Overloaded 'fetchField2' method not allowed in class 'wiringtests.TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classTypeIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass2\n" +
                "}\n" +
                "\n" +
                "type TestClass2 {\n" +
                "    field2(option: String): Int\n" +
                "}");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.TestClass2' is not public",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void isGetterOnNotBooleanTypes() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "schema {\n" +
                "    query: TestClass1\n" +
                "}\n" +
                "\n" +
                "type TestClass1 {\n" +
                "    field3: Int\n" +
                "    field4: Boolean\n" +
                "}");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'field3' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void resolveSimpleFields() throws Exception {
        String result = executeQuery("" +
                "schema {\n" +
                "    query: TestClass3\n" +
                "}\n" +
                "\n" +
                "type TestClass3 {\n" +
                "    simplefield: String\n" +
                "    fieldwithargs(a: Int, b: Int): String\n" +
                "}",
                "{ simplefield,  fieldwithargs(a:10, b:40) }");
        assertEquals(
                "{simplefield=response, fieldwithargs=10, 40}",
                result);
    }

    @Test
    public void resolveScalarReturns() throws Exception {
        String result = executeQuery("" +
                        "schema {\n" +
                        "    query: TestClass3\n" +
                        "}\n" +
                        "\n" +
                        "type TestClass3 {\n" +
                        "    booleanScalar:          Boolean\n" +
                        "    booleanPrimitiveScalar: Boolean\n" +
                        "    integerScalar:          Int\n" +
                        "    integerPrimitiveScalar: Int\n" +
                        "    doubleScalar:           Float\n" +
                        "    doublePrimitiveScalar:  Float\n" +
                        "    floatScalar:            Float\n" +
                        "    floatPrimitiveScalar:   Float\n" +
                        "    stringScalar:           String\n" +
                        "    IDScalar:               ID\n" +
                        "}",
                "{ booleanScalar, booleanPrimitiveScalar, integerScalar, integerPrimitiveScalar," +
                        "doubleScalar, doublePrimitiveScalar, floatScalar, floatPrimitiveScalar," +
                        "stringScalar, IDScalar }");
        assertEquals(
                "{booleanScalar=true, booleanPrimitiveScalar=false, integerScalar=1, integerPrimitiveScalar=2, " +
                        "doubleScalar=3.0, doublePrimitiveScalar=4.0, floatScalar=5.0, floatPrimitiveScalar=6.0, " +
                        "stringScalar=string result, IDScalar=ID result}",
                result);
    }

    @Test
    public void resolveListReturn() throws Exception {
        String result = executeQuery("" +
                        "schema {\n" +
                        "    query: TestClass3\n" +
                        "}\n" +
                        "\n" +
                        "type TestClass3 {\n" +
                        "    arrayList: [Int]\n" +
                        "    listList: [TestClass4]\n" +
                        "}" +
                        "type TestClass4 {\n" +
                        "    field1: String\n" +
                        "    field2: Int\n" +
                        "}\n",

                "{ arrayList, listList { field2 } }");
        assertEquals(
                "{arrayList=[3, 4], listList=[{field2=42}]}",
                result);
    }

    @Test
    public void resolveListParameter() throws Exception {
        String result = executeQuery("" +
                        "schema {\n" +
                        "    query: TestClass3\n" +
                        "}\n" +
                        "\n" +
                        "type TestClass3 {\n" +
                        "    fieldWithListParam(arg: [Int]): String\n" +
                        "}",

                "{ fieldWithListParam(arg: [1, 2, 3]) }");
        assertEquals(
                "{fieldWithListParam=1_2_3}",
                result);
    }

    @Test
    public void resolveObjectReturn() throws Exception {
        String result = executeQuery("" +
                        "schema {\n" +
                        "    query: TestClass3\n" +
                        "}\n" +
                        "\n" +
                        "type TestClass3 {\n" +
                        "    test4: TestClass4\n" +
                        "}\n" +
                        "type TestClass4 {\n" +
                        "    field1: String\n" +
                        "    field2: Int\n" +
                        "}\n",
                "{ test4 { field1, field2 } }");
        assertEquals(
                "{test4={field1=result_field_1, field2=42}}",
                result);
    }

    @Test
    public void resolveObjectParam() throws Exception {
        String result = executeQuery("" +
                        "schema {\n" +
                        "    query: TestClass3\n" +
                        "}\n" +
                        "\n" +
                        "type TestClass3 {\n" +
                        "    objectArg(obj: InputTestClass): String\n" +
                        "}\n" +
                        "input InputTestClass {\n" +
                        "    field1: String\n" +
                        "    field2: Int\n" +
                        "}\n",
                "{ objectArg(obj: { field1: \"hello\", field2: 1 }) }");
        assertEquals(
                "{objectArg=hello1}",
                result);
    }
}