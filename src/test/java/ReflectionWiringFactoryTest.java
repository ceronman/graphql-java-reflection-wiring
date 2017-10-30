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

    private String executeQuery(String pkg, String schema, String query) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory(pkg, typeDefinitionRegistry);
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

    private String executeQuery(String schema, String query) {
        return executeQuery("wiringtests", schema, query);
    }

    @Test
    public void missingClassError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: NonExistentClass                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type NonExistentClass {                              \n" +
                        "        hello: String                                    \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.NonExistentClass' not found",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void missingFieldError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        hello: String                                    \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'hello' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badGetterReturnTypeError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        intField: String                                 \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'getIntField' in class 'wiringtests.TestClass1' returns 'int' instead of expected " +
                        "'TypeName{name='String'}'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badFetcherReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        floatField: Boolean                              \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchFloatField' in class 'wiringtests.TestClass1' returns 'float' instead of " +
                        "expected 'TypeName{name='Boolean'}'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void badFetcherListReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        streamField: [Int]                               \n" +
                        "        listField: [Int]                                 \n" +
                        "    }");
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
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        noEnv: Boolean                                   \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchNoEnv' in class 'wiringtests.TestClass1' doesn't have DataFetchingEnvironment " +
                        "as first parameter",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherBadArgumentOrder() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        badOrder: String                                 \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchBadOrder' in class 'wiringtests.TestClass1' doesn't have " +
                        "DataFetchingEnvironment as first parameter",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherMissingArguments() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        manyArguments(a: Int, b: String, c: Boolean): Int\n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchManyArguments' in class 'wiringtests.TestClass1' doesn't have the right " +
                        "number of arguments",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherExtraArguments() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        manyArguments(a: Int): Int                       \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchManyArguments' in class 'wiringtests.TestClass1' doesn't have the right " +
                        "number of arguments",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherSignatureMismatch() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        manyArguments(a: String, b: Int): Int            \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Type mismatch in method 'fetchManyArguments', argument '1' in class " +
                        "'wiringtests.TestClass1' expected 'TypeName{name='String'}', got 'int'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void getterIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        someField: Float                                 \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'someField' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        field1: Boolean                                  \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'field1' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherIsOverloaded() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        field2: Int                                      \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Overloaded 'fetchField2' method not allowed in class 'wiringtests.TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classTypeIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass2                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass2 {                                    \n" +
                        "        field2(option: String): Int                      \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.TestClass2' is not public",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void isGetterOnNotBooleanTypes() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                        "    schema {                                             \n" +
                        "        query: TestClass1                                \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TestClass1 {                                    \n" +
                        "        field3: Int                                      \n" +
                        "        field4: Boolean                                  \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'field3' of type 'TestClass1'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classDoesNotImplementInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "    schema {                                             \n" +
                "        query: TestClass3                                \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass3 {                                    \n" +
                "        interfaceField: TestInterface                    \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface TestInterface {                            \n" +
                "        field1: String                                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass4 implements TestInterface {           \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.TestClass4' does not implement interface 'wiringtests.TestInterface'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void interfaceDoesNotDefineMethod() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "    schema {                                             \n" +
                "        query: TestClass3                                \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass3 {                                    \n" +
                "        interfaceField: TestInterface                    \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface TestInterface {                            \n" +
                "        field2: Int                                      \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass5 implements TestInterface {           \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Interface 'wiringtests.TestInterface' does not define properly define method 'field2'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classIsNotInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("" +
                "    schema {                                             \n" +
                "        query: TestClass3                                \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass3 {                                    \n" +
                "        testClass6: TestClass6                           \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface TestClass6 {                               \n" +
                "        field1: String                                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass5 implements TestClass6 {              \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'wiringtests.TestClass6' is not an interface but defined in GraphQL as interface",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void resolveSimpleFields() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        simplefield: String                          \n" +
                        "        fieldwithargs(a: Int, b: Int): String        \n" +
                        "    }",
                "{ simplefield,  fieldwithargs(a:10, b:40) }");
        assertEquals(
                "{simplefield=response, fieldwithargs=10, 40}",
                result);
    }

    @Test
    public void resolveScalarReturns() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        booleanScalar:          Boolean              \n" +
                        "        booleanPrimitiveScalar: Boolean              \n" +
                        "        integerScalar:          Int                  \n" +
                        "        integerPrimitiveScalar: Int                  \n" +
                        "        doubleScalar:           Float                \n" +
                        "        doublePrimitiveScalar:  Float                \n" +
                        "        floatScalar:            Float                \n" +
                        "        floatPrimitiveScalar:   Float                \n" +
                        "        stringScalar:           String               \n" +
                        "        IDScalar:               ID                   \n" +
                        "    }",
                "    { booleanScalar, booleanPrimitiveScalar, integerScalar, integerPrimitiveScalar," +
                        "    doubleScalar, doublePrimitiveScalar, floatScalar, floatPrimitiveScalar," +
                        "stringScalar, IDScalar }");
        assertEquals(
                "{booleanScalar=true, booleanPrimitiveScalar=false, integerScalar=1, " +
                        "integerPrimitiveScalar=2, doubleScalar=3.0, doublePrimitiveScalar=4.0, " +
                        "floatScalar=5.0, floatPrimitiveScalar=6.0, stringScalar=string result, " +
                        "IDScalar=ID result}",
                result);
    }

    @Test
    public void resolveListReturn() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        arrayList: [Int]                             \n" +
                        "        listList: [TestClass4]                       \n" +
                        "    }" +
                        "    type TestClass4 {                                \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",

                "{ arrayList, listList { field2 } }");
        assertEquals(
                "{arrayList=[3, 4], listList=[{field2=42}]}",
                result);
    }

    @Test
    public void resolveListParameter() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        fieldWithListParam(arg: [Int]): String       \n" +
                        "    }",

                "{ fieldWithListParam(arg: [1, 2, 3]) }");
        assertEquals(
                "{fieldWithListParam=1_2_3}",
                result);
    }

    @Test
    public void resolveObjectReturn() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        test4: TestClass4                            \n" +
                        "    }                                                \n" +
                        "    type TestClass4 {                                \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",
                "{ test4 { field1, field2 } }");
        assertEquals(
                "{test4={field1=result_field_1, field2=42}}",
                result);
    }

    @Test
    public void resolveObjectParam() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        objectArg(obj: InputTestClass): String       \n" +
                        "    }                                                \n" +
                        "    input InputTestClass {                           \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",
                "{ objectArg(obj: { field1: \"hello\", field2: 1 }) }");
        assertEquals(
                "{objectArg=hello1}",
                result);
    }

    @Test
    public void resolveEnumParam() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        enumArg(e: TestEnum): String                 \n" +
                        "    }                                                \n" +
                        "    enum TestEnum {                                  \n" +
                        "        ONE                                          \n" +
                        "        TWO                                          \n" +
                        "        THREE                                        \n" +
                        "    }                                                \n",
                "{ enumArg(e: THREE) }");
        assertEquals(
                "{enumArg=THREE}",
                result);
    }

    @Test
    public void resolveEnumReturn() throws Exception {
        String result = executeQuery("" +
                        "    schema {                                         \n" +
                        "        query: TestClass3                            \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TestClass3 {                                \n" +
                        "        enumReturn: TestEnum                         \n" +
                        "    }                                                \n" +
                        "    enum TestEnum {                                  \n" +
                        "        ONE                                          \n" +
                        "        TWO                                          \n" +
                        "        THREE                                        \n" +
                        "    }                                                \n",
                "{ enumReturn }");
        assertEquals(
                "{enumReturn=ONE}",
                result);
    }

    @Test
    public void resolveInterfaceReturn() throws Exception {
        String result = executeQuery("" +
                "    schema {                                             \n" +
                "        query: TestClass3                                \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass3 {                                    \n" +
                "        interfaceField2: TestInterface2                  \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface TestInterface2 {                           \n" +
                "        stringField: String                              \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TestClass7 implements TestInterface2 {          \n" +
                "        stringField: String                              \n" +
                "        intField: Int                                    \n" +
                "    }                                                    \n",
                "{ interfaceField2 { stringField } }");
        assertEquals(
                "{interfaceField2={stringField=string}}",
                result);
    }

    @Test
    public void resolveUnionReturn() throws Exception {
        String result = executeQuery("uniontests", "" +
                        "    schema {                                             \n" +
                        "        query: UnionTestQuery                            \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type UnionTestQuery {                                \n" +
                        "        unionFieldA: TestUnion                           \n" +
                        "        unionFieldB: TestUnion                           \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    union TestUnion = TypeA | TypeB                      \n" +
                        "                                                         \n" +
                        "    type TypeA {                                         \n" +
                        "        stringField: String                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TypeB {                                         \n" +
                        "        intField: Int                                    \n" +
                        "    }                                                    \n",
                "{ unionFieldA{ ... on TypeA { stringField }, " +
                        "              ... on TypeB { intField } }," +
                        " unionFieldB{ ... on TypeA { stringField }, " +
                        "              ... on TypeB { intField } } }");
        assertEquals(
                "{unionFieldA={stringField=string}, unionFieldB={intField=42}}",
                result);
    }
}