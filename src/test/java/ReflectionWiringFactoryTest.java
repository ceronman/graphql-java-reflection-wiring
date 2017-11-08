import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static org.junit.Assert.*;

public class ReflectionWiringFactoryTest {

    private ReflectionWiringFactory wireSchema(String schema) {
        return wireSchema("wiringtests", schema);
    }

    private ReflectionWiringFactory wireSchema(Collection<Class<?>> classes, String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory(typeDefinitionRegistry, classes);
        RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(wiringFactory).build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        return wiringFactory;
    }

    private ReflectionWiringFactory wireSchema(String pkg, String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory(typeDefinitionRegistry, pkg);
        RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(wiringFactory).build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        return wiringFactory;
    }

    private String executeQuery(String pkg, String schema, String query) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory(typeDefinitionRegistry, pkg);
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
                "Class for type 'NonExistentClass' was not found",
                wiringFactory.getErrors().get(0));
    }

    public class MissingFieldTest {
    }

    @Test
    public void missingResolverError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(MissingFieldTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: MissingFieldTest                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type MissingFieldTest {                              \n" +
                        "        hello: String                                    \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'hello' of type 'MissingFieldTest'",
                wiringFactory.getErrors().get(0));
    }

    public class BadGetterReturnTypeTest {
        public int getField() { return 1; }
    }

    @Test
    public void badGetterReturnTypeError() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(BadGetterReturnTypeTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: BadGetterReturnTypeTest                   \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BadGetterReturnTypeTest {                       \n" +
                        "        field: String                                    \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'getField' in class 'BadGetterReturnTypeTest' returns 'int' " +
                        "instead of expected 'String'",
                wiringFactory.getErrors().get(0));
    }

    public class BadFetcherReturnTypeTest {
        public float fetchField(DataFetchingEnvironment env) { return 1.0f; }
    }

    @Test
    public void badFetcherReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(BadFetcherReturnTypeTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: BadFetcherReturnTypeTest                  \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BadFetcherReturnTypeTest {                      \n" +
                        "        field: Boolean                                   \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchField' in class 'BadFetcherReturnTypeTest' returns 'float' " +
                        "instead of expected 'Boolean'",
                wiringFactory.getErrors().get(0));
    }

    public class BadListTest {
        public Stream<Integer> fetchStreamField(DataFetchingEnvironment env) {
            return Stream.of(1, 2, 3);
        }
        public List fetchListField(DataFetchingEnvironment env) {
            return new ArrayList();
        }
    }

    @Test
    public void badFetcherListReturnType() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(BadListTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: BadListTest                               \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BadListTest {                                   \n" +
                        "        streamField: [Int]                               \n" +
                        "        listField: [Int]                                 \n" +
                        "    }");
        assertEquals(4, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchStreamField' in class 'BadListTest' returns 'Stream' instead of expected '[Int]'",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Method 'fetchListField' in class 'BadListTest' returns 'List' instead of expected '[Int]'",
                wiringFactory.getErrors().get(2));
    }

    public class NoEnvArgTest {
        public boolean fetchField() { return true; }
    }

    @Test
    public void fetcherWithoutEnvArgument() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(NoEnvArgTest.class),
                "" +
                        "    schema {                                             \n" +
                        "        query: NoEnvArgTest                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type NoEnvArgTest {                                  \n" +
                        "        field: Boolean                                   \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchField' in class 'NoEnvArgTest' doesn't have DataFetchingEnvironment " +
                        "as first parameter",
                wiringFactory.getErrors().get(0));
    }

    public class BadArgOrderTest {
        public String fetchField(int arg, DataFetchingEnvironment env) {
            return "";
        }
    }

    @Test
    public void fetcherBadArgumentOrder() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(BadArgOrderTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: BadArgOrderTest                           \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BadArgOrderTest {                               \n" +
                        "        field: String                                    \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchField' in class 'BadArgOrderTest' doesn't have " +
                        "DataFetchingEnvironment as first parameter",
                wiringFactory.getErrors().get(0));
    }

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

    @Test
    public void fetcherArgumentsMissmatch() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(ArgsMismatchTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: ArgsMismatchTest                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type ArgsMismatchTest {                              \n" +
                        "        missingArg(a: Int, b: String, c: Boolean): Int   \n" +
                        "        extraArg(a: Int): Int                            \n" +
                        "        wrongArgs(a: String, b: String): Int             \n" +
                        "    }");
        assertEquals(6, wiringFactory.getErrors().size());
        assertEquals(
                "Method 'fetchMissingArg' in class 'ArgsMismatchTest' doesn't have the right " +
                        "number of arguments",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Method 'fetchExtraArg' in class 'ArgsMismatchTest' doesn't have the right " +
                        "number of arguments",
                wiringFactory.getErrors().get(2));
        assertEquals(
                "Type mismatch in method 'fetchWrongArgs', argument '1' in class " +
                        "'ArgsMismatchTest' expected 'String', got 'int'",
                wiringFactory.getErrors().get(4));
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
                "Overloaded 'fetchField2' method not allowed in class 'TestClass1'",
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
                "Class 'TestClass2' is not public",
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
                "Class 'TestClass4' does not implement interface 'TestInterface'",
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
                "Interface 'TestInterface' does not define properly define method 'field2'",
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
                "Class 'TestClass6' is not an interface but defined in GraphQL as interface",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void unionIsNotInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("uniontests", "" +
                        "    schema {                                             \n" +
                        "        query: NotInterfaceUnionQuery                    \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type NotInterfaceUnionQuery {                        \n" +
                        "        unionFieldA: NotInterfaceUnion                   \n" +
                        "        unionFieldB: NotInterfaceUnion                   \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    union NotInterfaceUnion = TypeC | TypeD              \n" +
                        "                                                         \n" +
                        "    type TypeC {                                         \n" +
                        "        stringField: String                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TypeD {                                         \n" +
                        "        intField: Int                                    \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'NotInterfaceUnion' is not an interface but defined in GraphQL as Union",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void unionInterfaceWithMethods() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("uniontests", "" +
                "    schema {                                             \n" +
                "        query: BadUnionTestQuery                         \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type BadUnionTestQuery {                             \n" +
                "        unionFieldA: InterfaceWithMethodsUnion           \n" +
                "        unionFieldB: InterfaceWithMethodsUnion           \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    union InterfaceWithMethodsUnion = BadTypeA | BadTypeB\n" +
                "                                                         \n" +
                "    type BadTypeA {                                      \n" +
                "        stringField: String                              \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type BadTypeB {                                      \n" +
                "        intField: Int                                    \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Interface 'InterfaceWithMethodsUnion' should not have methods, " +
                        "it's mapped as a GraphQL Union",
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