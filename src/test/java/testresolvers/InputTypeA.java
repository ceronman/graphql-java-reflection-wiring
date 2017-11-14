package testresolvers;

import java.util.Map;

public class InputTypeA {
    private String field1;
    private int field2;

    public InputTypeA(Map<String, Object> source) {
        field1 = (String) source.get("field1");
        field2 = (int) source.get("field2");
    }

    public String getField1() { return field1; }
    public int getField2() { return field2; }
}
