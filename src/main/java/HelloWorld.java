import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.language.*;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.*;
import resolvers.Hotel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

public class HelloWorld {

    public static void main(String[] args) {
        SchemaParser schemaParser = new SchemaParser();

        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(loadSchema());

        ReflectionWiringFactory wiringFactory = new ReflectionWiringFactory("resolvers", typeDefinitionRegistry);

        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .build();


        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        if (wiringFactory.getErrors().size() > 0) {
            for (String error : wiringFactory.getErrors()) {
                System.out.println(error);
            }
        }

        GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
        ExecutionResult executionResult = build.execute("{ hotel(id:444) { id, name }, hello }");

        if (executionResult.getErrors().size() > 0) {
            System.out.println("ERRORS: ");
            for (GraphQLError error : executionResult.getErrors()) {
                System.out.println(error.getMessage());
            }
        }

        System.out.println(executionResult.getData().toString());
        // Prints: {hello=world}
    }

    public static TypeRuntimeWiring.Builder buildQuery(TypeRuntimeWiring.Builder builder) {
        return builder.dataFetcher("hotel", d -> new Hotel(123, "Test hotel"))
                       .dataFetcher("hello", new StaticDataFetcher("world"));
    }

    public static File loadSchema() {
        return new File(HelloWorld.class.getResource("schema.graphqls").getFile());
    }
}