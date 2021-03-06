import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.Test;
import testresolvers.NoEnvArgTest;
import testresolvers.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static org.junit.Assert.assertEquals;

public class ReflectionWiringFactoryTest {
    private ReflectionWiringFactory wireSchema(Collection<Class<?>> classes, String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        return new ReflectionWiringFactory(typeDefinitionRegistry, classes);
    }

    private ReflectionWiringFactory wireSchema(String pkg, String schema) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        return new ReflectionWiringFactory(typeDefinitionRegistry, pkg);
    }

    private String executeQuery(Collection<Class<?>> classes, String schema, String query) {
        return executeQuery(classes, schema, query, null);
    }

    private String executeQuery(Collection<Class<?>> classes, String schema, String query, Object context) {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory(typeDefinitionRegistry, classes);
        for (String error : wiringFactory.getErrors()) {
            throw new RuntimeException(error);
        }
        RuntimeWiring runtimeWiring = newRuntimeWiring().wiringFactory(wiringFactory).build();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(context)
                .build();
        ExecutionResult executionResult = build.execute(executionInput);
        for (GraphQLError error : executionResult.getErrors()) {
            throw new RuntimeException(error.toString());
        }
        return executionResult.getData().toString();
    }

    @Test
    public void missingClass() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema("testresolvers", "" +
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

    @Test
    public void missingResolver() throws Exception {
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

    @Test
    public void missingInputObjectField() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(MissingInputFieldQuery.class, InputTypeA.class), "" +
                        "    schema {                                             \n" +
                        "        query: MissingInputFieldQuery                    \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    input InputTypeA {                                   \n" +
                        "        field1: String                                   \n" +
                        "        field2: Int                                      \n" +
                        "        missingField: Float                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type MissingInputFieldQuery {                        \n" +
                        "        field(arg: InputTypeA): String                   \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Input type 'InputTypeA' doesn't have a getter for field 'missingField'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void inputTypeWithoutConstructor() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(BadInputTypeQuery.class, BadInputType.class), "" +
                        "    schema {                                             \n" +
                        "        query: BadInputTypeQuery                         \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    input BadInputType {                                 \n" +
                        "        field1: String                                   \n" +
                        "        field2: Int                                      \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BadInputTypeQuery {                             \n" +
                        "        field(arg: BadInputType): String                 \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "InputType BadInputType doesn't have a Map<String,Object> constructor",
                wiringFactory.getErrors().get(0));
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
    public void resolverIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(PrivateResolverTest.class),"" +
                        "    schema {                                             \n" +
                        "        query: PrivateResolverTest                       \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type PrivateResolverTest {                           \n" +
                        "        field1: Float                                    \n" +
                        "        field2: Int                                      \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'field1' of type 'PrivateResolverTest'",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Unable to find resolver for field 'field2' of type 'PrivateResolverTest'",
                wiringFactory.getErrors().get(1));
    }

    @Test
    public void classIsNotPublic() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                "testresolvers", "" +
                        "    schema {                                             \n" +
                        "        query: PrivateClassTest                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type PrivateClassTest {                              \n" +
                        "        field: Int                                       \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'PrivateClassTest' is not public",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classWithoutDefaultConstructor() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                "testresolvers", "" +
                        "    schema {                                             \n" +
                        "        query: ClassWithoutDefaultConstructor            \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type ClassWithoutDefaultConstructor {                \n" +
                        "        field: Int                                       \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'ClassWithoutDefaultConstructor' is root query and doesn't have a default " +
                        "constructor but it has non-static resolvers",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void fetcherIsOverloaded() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(OverloadedMethodTest.class), "" +
                "    schema {                                                    \n" +
                "        query: OverloadedMethodTest                             \n" +
                "    }                                                           \n" +
                "                                                                \n" +
                "    type OverloadedMethodTest {                                 \n" +
                "        field1: String                                          \n" +
                "        field2: Int                                             \n" +
                "    }");
        assertEquals(4, wiringFactory.getErrors().size());
        assertEquals(
                "Overloaded 'getField1' method not allowed in class 'OverloadedMethodTest'",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Overloaded 'fetchField2' method not allowed in class 'OverloadedMethodTest'",
                wiringFactory.getErrors().get(2));
    }

    @Test
    public void isGetterOnNotBooleanTypes() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Collections.singletonList(BoolGetterTest.class), "" +
                        "    schema {                                             \n" +
                        "        query: BoolGetterTest                            \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BoolGetterTest {                                \n" +
                        "        field1: Boolean                                  \n" +
                        "        field2: Int                                      \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Unable to find resolver for field 'field2' of type 'BoolGetterTest'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void enumValueMismatch() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(BadEnumTestQuery.class, BadEnum1.class, BadEnum2.class),
                "" +
                        "    schema {                                             \n" +
                        "        query: BadEnumTestQuery                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "   enum BadEnum1 { ONE TWO MISSING }                     \n" +
                        "   enum BadEnum2 { A B }                                 \n" +
                        "                                                         \n" +
                        "    type BadEnumTestQuery {                              \n" +
                        "        field1: BadEnum1                                 \n" +
                        "        field2: BadEnum2                                 \n" +
                        "    }");
        assertEquals(2, wiringFactory.getErrors().size());
        assertEquals(
                "Java Enum 'BadEnum1' doesn't have the same values as GraphQL Enum 'BadEnum1'",
                wiringFactory.getErrors().get(0));
        assertEquals(
                "Java Enum 'BadEnum2' doesn't have the same values as GraphQL Enum 'BadEnum2'",
                wiringFactory.getErrors().get(1));
    }

    @Test
    public void enumIsNotEnum() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(NotEnumTestQuery.class, NotEnum.class), "" +
                        "    schema {                                             \n" +
                        "        query: NotEnumTestQuery                          \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    enum NotEnum { ONE TWO }                             \n" +
                        "                                                         \n" +
                        "    type NotEnumTestQuery {                              \n" +
                        "        field: NotEnum                                   \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'NotEnum' is not Enum",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classDoesNotImplementInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(BadInterfaceTestQuery.class, TypeA.class, NotImplementedInterface.class),
                "" +
                "    schema {                                             \n" +
                "        query: BadInterfaceTestQuery                     \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type BadInterfaceTestQuery {                         \n" +
                "        field: NotImplementedInterface                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface NotImplementedInterface {                  \n" +
                "        field1: String                                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TypeA implements NotImplementedInterface {      \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'TypeA' does not implement interface 'NotImplementedInterface'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void interfaceDoesNotDefineMethod() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(MissingFieldTestQuery.class, TypeB.class, MissingFieldInterface.class),
                "" +
                "    schema {                                             \n" +
                "        query: MissingFieldTestQuery                     \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type MissingFieldTestQuery {                         \n" +
                "        field: MissingFieldInterface                     \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface MissingFieldInterface {                    \n" +
                "        field2: Int                                      \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TypeB implements MissingFieldInterface {        \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Interface 'MissingFieldInterface' does not define properly define method 'field2'",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void classIsNotInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(NotInterfaceTest.class, NotInterface.class, TypeC.class),
                "" +
                "    schema {                                             \n" +
                "        query: NotInterfaceTest                          \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type NotInterfaceTest {                              \n" +
                "        field: NotInterface                              \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface NotInterface {                             \n" +
                "        field1: String                                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TypeC implements NotInterface {                 \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'NotInterface' is not an interface but defined in GraphQL as interface",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void unionIsNotInterface() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(NotInterfaceUnionQuery.class, NotInterfaceUnion.class, UTypeA.class, UTypeB.class),
                "" +
                        "    schema {                                             \n" +
                        "        query: NotInterfaceUnionQuery                    \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type NotInterfaceUnionQuery {                        \n" +
                        "        unionFieldA: NotInterfaceUnion                   \n" +
                        "        unionFieldB: NotInterfaceUnion                   \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    union NotInterfaceUnion = UTypeA | UTypeB            \n" +
                        "                                                         \n" +
                        "    type UTypeA {                                        \n" +
                        "        stringField: String                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type UTypeB {                                        \n" +
                        "        intField: Int                                    \n" +
                        "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Class 'NotInterfaceUnion' is not an interface but defined in GraphQL as Union",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void unionInterfaceWithMethods() throws Exception {
        ReflectionWiringFactory wiringFactory = wireSchema(
                Arrays.asList(BadUnionTestQuery.class, InterfaceWithMethodsUnion.class, UTypeC.class, UTypeD.class),
                "" +
                "    schema {                                             \n" +
                "        query: BadUnionTestQuery                         \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type BadUnionTestQuery {                             \n" +
                "        unionFieldA: InterfaceWithMethodsUnion           \n" +
                "        unionFieldB: InterfaceWithMethodsUnion           \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    union InterfaceWithMethodsUnion = UTypeC | UTypeD    \n" +
                "                                                         \n" +
                "    type UTypeC {                                        \n" +
                "        stringField: String                              \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type UTypeD {                                        \n" +
                "        intField: Int                                    \n" +
                "    }");
        assertEquals(1, wiringFactory.getErrors().size());
        assertEquals(
                "Interface 'InterfaceWithMethodsUnion' should not have methods, " +
                        "it's mapped as a GraphQL Union",
                wiringFactory.getErrors().get(0));
    }

    @Test
    public void resolveScalars() throws Exception {
        String result = executeQuery(
                Collections.singletonList(ScalarTestQuery.class),
                "" +
                        "    schema {                                         \n" +
                        "        query: ScalarTestQuery                       \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type ScalarTestQuery {                           \n" +
                        "        booleanScalar:          Boolean              \n" +
                        "        booleanPrimitiveScalar: Boolean              \n" +
                        "        integerScalar:          Int                  \n" +
                        "        integerPrimitiveScalar: Int                  \n" +
                        "        doubleScalar:           Float                \n" +
                        "        doublePrimitiveScalar:  Float                \n" +
                        "        stringScalar:           String               \n" +
                        "        IDScalar:               ID                   \n" +
                        "                                                     \n" +
                        "        field(boolArg: Boolean, boolArg2: Boolean,   \n" +
                        "              intArg: Int, intArg2: Int,             \n" +
                        "              doubleArg: Float, doubleArg2: Float,   \n" +
                        "              strArg:String, idArg: ID): String      \n" +
                        "    }",
                "" +
                        "{                                                    \n" +
                        "   booleanScalar                                     \n" +
                        "   booleanPrimitiveScalar                            \n" +
                        "   integerScalar                                     \n" +
                        "   integerPrimitiveScalar                            \n" +
                        "   doubleScalar                                      \n" +
                        "   doublePrimitiveScalar                             \n" +
                        "   stringScalar                                      \n" +
                        "   IDScalar                                          \n" +
                        "                                                     \n" +
                        "   field(boolArg:true, boolArg2:false,               \n" +
                        "         intArg:1, intArg2:2,                        \n" +
                        "         doubleArg:3.0, doubleArg2:4.0,              \n" +
                        "         strArg:\"str\", idArg:\"id\")               \n" +
                        "}");
        assertEquals(
                "{booleanScalar=true, booleanPrimitiveScalar=false, " +
                        "integerScalar=1, integerPrimitiveScalar=2, " +
                        "doubleScalar=3.0, doublePrimitiveScalar=4.0, " +
                        "stringScalar=string result, IDScalar=ID result, " +
                        "field=true_false_1_2_3.0_4.0_str_id}",
                result);
    }

    @Test
    public void resolveLists() throws Exception {
        String result = executeQuery(
                Arrays.asList(ListTestQuery.class, TypeA.class),
                "" +
                        "    schema {                                         \n" +
                        "        query: ListTestQuery                         \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type ListTestQuery {                             \n" +
                        "        field1: [Int]                                \n" +
                        "        field2: [TypeA]                              \n" +
                        "        field3(arg: [Int]): String                   \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TypeA {                                     \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",

                "{ field1, field2 { field2 }, field3(arg: [1, 2, 3]) }");
        assertEquals(
                "{field1=[3, 4], field2=[{field2=0}], field3=1_2_3}",
                result);
    }

    @Test
    public void resolveNotNull() throws Exception {
        String result = executeQuery(
                Arrays.asList(NotNullTestQuery.class, TypeA.class),
                "" +
                        "    schema {                                         \n" +
                        "        query: NotNullTestQuery                      \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type NotNullTestQuery {                          \n" +
                        "        field1: Int!                                 \n" +
                        "        field2: TypeA!                               \n" +
                        "        field3: [String!]                            \n" +
                        "        field4: [Float]!                             \n" +
                        "        field5: [TypeA!]!                            \n" +
                        "        field6(arg: String!): String                 \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TypeA {                                     \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",

                "{ field1, field2 { field2 }, field3, field4, field5 { field1 }, field6(arg: \"hello\") }");
        assertEquals(
                "{field1=1, field2={field2=0}, field3=[], field4=[], field5=[], field6=hello}",
                result);
    }

    @Test
    public void resolveObjects() throws Exception {
        String result = executeQuery(
                Arrays.asList(ObjectTestQuery.class, TypeA.class, InputTypeA.class),
                "" +
                        "    schema {                                         \n" +
                        "        query: ObjectTestQuery                       \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type ObjectTestQuery {                           \n" +
                        "        fieldA: TypeA                                \n" +
                        "        fieldB(obj: InputTypeA): String              \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type TypeA {                                     \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    input InputTypeA {                               \n" +
                        "        field1: String                               \n" +
                        "        field2: Int                                  \n" +
                        "    }                                                \n",
                "{ fieldA { field1, field2 }, fieldB(obj: { field1: \"hello\", field2: 1 }) }");
        assertEquals(
                "{fieldA={field1=hello, field2=0}, fieldB=hello1}",
                result);
    }

    @Test
    public void resolveEnums() throws Exception {
        String result = executeQuery(
                Arrays.asList(EnumTestQuery.class, TestEnum.class),
                "" +
                        "    schema {                                         \n" +
                        "        query: EnumTestQuery                         \n" +
                        "    }                                                \n" +
                        "                                                     \n" +
                        "    type EnumTestQuery {                             \n" +
                        "        field1(e: TestEnum): String                  \n" +
                        "        field2: TestEnum                             \n" +
                        "    }                                                \n" +
                        "    enum TestEnum {                                  \n" +
                        "        ONE                                          \n" +
                        "        TWO                                          \n" +
                        "        THREE                                        \n" +
                        "    }                                                \n",
                "{ field1(e: THREE), field2 }");
        assertEquals(
                "{field1=THREE, field2=ONE}",
                result);
    }

    @Test
    public void resolveInterface() throws Exception {
        String result = executeQuery(
                Arrays.asList(InterfaceTestQuery.class, TestInterface.class, TypeD.class),
                "" +
                "    schema {                                             \n" +
                "        query: InterfaceTestQuery                        \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type InterfaceTestQuery {                            \n" +
                "        fieldA: TestInterface                            \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    interface TestInterface {                            \n" +
                "        field1: String                                   \n" +
                "    }                                                    \n" +
                "                                                         \n" +
                "    type TypeD implements TestInterface {                \n" +
                "        field1: String                                   \n" +
                "        field2: Int                                      \n" +
                "    }                                                    \n",
                "{ fieldA { field1 } }");
        assertEquals(
                "{fieldA={field1=hello}}",
                result);
    }

    @Test
    public void resolveUnionReturn() throws Exception {
        String result = executeQuery(
                Arrays.asList(UnionTestQuery.class, TestUnion.class, TypeWithString.class, TypeWithInt.class),
                "" +
                        "    schema {                                             \n" +
                        "        query: UnionTestQuery                            \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type UnionTestQuery {                                \n" +
                        "        unionFieldA: TestUnion                           \n" +
                        "        unionFieldB: TestUnion                           \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    union TestUnion = TypeWithString | TypeWithInt       \n" +
                        "                                                         \n" +
                        "    type TypeWithString {                                \n" +
                        "        stringField: String                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type TypeWithInt {                                   \n" +
                        "        intField: Int                                    \n" +
                        "    }                                                    \n",
                "{ unionFieldA{ ... on TypeWithString { stringField }, " +
                        "              ... on TypeWithInt { intField } }," +
                        " unionFieldB{ ... on TypeWithString { stringField }, " +
                        "              ... on TypeWithInt { intField } } }");
        assertEquals(
                "{unionFieldA={stringField=string}, unionFieldB={intField=42}}",
                result);
    }

    @Test
    public void resolveBatchedResolver() throws Exception {

        AtomicInteger queryCounter = new AtomicInteger();

        String result = executeQuery(
                Arrays.asList(BatchLoaderTest.class, Shop.class, Department.class, Product.class),
                "" +
                        "    schema {                                             \n" +
                        "        query: BatchLoaderTest                           \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type BatchLoaderTest {                               \n" +
                        "        shops: [Shop]                                    \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type Shop {                                          \n" +
                        "        id: Int                                          \n" +
                        "        name: String                                     \n" +
                        "        departments: [Department]                        \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type Department {                                    \n" +
                        "        id: Int                                          \n" +
                        "        name: String                                     \n" +
                        "        products: [Product]                              \n" +
                        "    }                                                    \n" +
                        "                                                         \n" +
                        "    type Product {                                       \n" +
                        "        id: Int                                          \n" +
                        "        name: String                                     \n" +
                        "    }                                                    \n",
                "{ shops { id, name, departments { id, name, products { id, name } } } }",
                queryCounter);

        assertEquals(
                "{shops=[{id=1, name=Shop #1, departments=[{id=101, name=Department #101, " +
                        "products=[{id=1010001, name=Product #1010001}, {id=1010002, name=Product #1010002}]}, " +
                        "{id=102, name=Department #102, " +
                        "products=[{id=1020001, name=Product #1020001}, {id=1020002, name=Product #1020002}]}]}, " +
                        "{id=2, name=Shop #2, departments=[{id=201, name=Department #201, " +
                        "products=[{id=2010001, name=Product #2010001}, {id=2010002, name=Product #2010002}]}, " +
                        "{id=202, name=Department #202, " +
                        "products=[{id=2020001, name=Product #2020001}, {id=2020002, name=Product #2020002}]}]}]}",
                result);

        assertEquals(1, queryCounter.intValue());
    }
}