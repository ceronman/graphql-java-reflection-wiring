import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BatchedDataLoaderTest {
    public static void main(String[] args) {
        System.out.println("Starting test...");

        SchemaParser schemaParser = new SchemaParser();
        File schemaFile = new File(BatchedDataLoaderTest.class.getResource("schema.graphqls").getFile());
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schemaFile);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, wiring);

        BatchLoader<Integer, Object> facilityBatchLoader = new BatchLoader<Integer, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<Integer> keys) {
                return CompletableFuture.supplyAsync(() -> {
                    System.out.println("Fetching Facilities!");
                    System.out.println(keys);
                    List<Object> facilities = new ArrayList();
                    for (int key : keys) {
                        Map<String, Object>  facility = new HashMap<>();
                        facility.put("id", key);
                        facility.put("name", "Facility" + key);
                        facilities.add((Object)facility);
                    }
                    return facilities;
                });
            }
        };

        DataLoader<Integer, Object> facilityDataLoader = new DataLoader<>(facilityBatchLoader);
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("facility", facilityDataLoader);

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(new DataLoaderDispatcherInstrumentation(registry))
                .build();

        String query = "{ hotel(id:1){ id, name, rooms { id, name, facilities { name } }  } }";

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(registry)
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        System.out.println(executionResult.getData().toString());
    }

    public static RuntimeWiring buildRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring()
                // this uses builder function lambda syntax
                .type("RootQuery", typeWiring -> typeWiring
                        .dataFetcher("hotel", e -> {
                            Map<String, Object> hotel = new HashMap<>();
                            hotel.put("id", e.getArgument("id"));
                            hotel.put("name", "MyHotel" + e.getArgument("id"));
                            return hotel;
                        })
                )
                .type("Hotel", typeWiring -> typeWiring
                        .dataFetcher("rooms", e -> {
                            Map<String, Object> hotel = e.getSource();
                            int base = (Integer)hotel.get("id") * 100;
                            List<Map<String, Object>> rooms = new ArrayList();
                            Map<String, Object> room = new HashMap<>();
                            room.put("id", base + 1);
                            room.put("name", "MyRoom" + (base + 1));
                            rooms.add(room);
                            room = new HashMap<>();
                            room.put("id", base + 2);
                            room.put("name", "MyRoom" + (base + 2));
                            rooms.add(room);
                            room = new HashMap<>();
                            room.put("id", base + 3);
                            room.put("name", "MyRoom" + (base + 3));
                            rooms.add(room);
                            return rooms;
                        })
                )
                // you can use builder syntax if you don't like the lambda syntax
                .type("Room", typeWiring -> typeWiring
                        .dataFetcher("facilities", e -> {
                            Map<String, Object> room = e.getSource();
                            int roomId = (int) room.get("id");
                            DataLoaderRegistry dlRegistry = e.getContext();
                            DataLoader<Integer, Object> facilityDL = dlRegistry.getDataLoader("facility");

                            List<Integer> facilityIds = IntStream.of(1, 2, 3)
                                    .boxed()
                                    .map(id -> roomId*1000 + id)
                                    .collect(Collectors.toList());

                            return facilityDL.loadMany(facilityIds);
                        })
                )
                .build();
    }
}
