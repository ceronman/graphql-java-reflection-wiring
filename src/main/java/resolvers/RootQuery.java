package resolvers;

import graphql.schema.DataFetchingEnvironment;

public class RootQuery {
    public static Hotel fetchHotel(DataFetchingEnvironment env, int id) {
        return new Hotel(id, "New name");
    }

    public String getHello() {
        return "world!";
    }
}
