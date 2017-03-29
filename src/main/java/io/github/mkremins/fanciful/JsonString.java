package io.github.mkremins.fanciful;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON string value.
 * Writes by this object will not write name values nor begin/end objects in the JSON stream.
 * All writes merely write the represented string value.
 */
final class JsonString implements JsonRepresentedObject {
    private String value;

    public JsonString(CharSequence value) {
        this.value = value == null ? null : value.toString();
    }

    @Override
    public void writeJson(JsonWriter writer) throws IOException {
        writer.value(getValue());
    }

    @Override
    public JsonRepresentedObject copy() {
        return new JsonString(value);
    }

    public String getValue() {
        return value;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("stringValue", value);
        return map;
    }

    @Override
    public String toString() {
        return value;
    }

    public static JsonString deserialize(Map<String, Object> map) {
        return new JsonString(map.get("stringValue").toString());
    }

}
