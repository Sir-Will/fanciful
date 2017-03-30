package io.github.mkremins.fanciful;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a textual component of a message part.
 * This can be used to not only represent string literals in a JSON message,
 * but also to represent localized strings and other text values.
 * <p>Different instances of this class can be created with static constructor methods.</p>
 */
public abstract class TextualComponent {
    static TextualComponent deserialize(Map<String, String> map) {
        if (map.containsKey("key") && map.size() == 2 && map.containsKey("value")) {
            // Arbitrary text component
            return ArbitraryTextTypeComponent.deserialize(map);
        } else if (map.size() >= 2 && map.containsKey("key") && !map.containsKey("value") /* It contains keys that START WITH value */) {
            // Complex JSON object
            return ComplexTextTypeComponent.deserialize(map);
        }

        return null;
    }

    static boolean isTextKey(String key) {
        return key.equals("translate") || key.equals("text") || key.equals("score") || key.equals("selector");
    }

    static boolean isTranslatableText(TextualComponent component) {
        return component instanceof ComplexTextTypeComponent && component.getKey().equals("translate");
    }

    /**
     * Create a textual component representing a string literal.
     * This is the default type of textual component when a single string literal is given to a method.
     *
     * @param textValue The text which will be represented.
     * @return The text component representing the specified literal text.
     */
    public static TextualComponent rawText(String textValue) {
        return new ArbitraryTextTypeComponent("text", textValue);
    }

    /**
     * Create a textual component representing a localized string. The client will see this text component as their
     * localized version of the specified string <em>key</em>, which can be overridden by a resource pack. <p> If the
     * specified translation key is not present on the client resource pack, the translation key will be displayed as a
     * string literal to the client. </p>
     *
     * @param translateKey The string key which maps to localized text.
     * @return The text component representing the specified localized text.
     */
    public static TextualComponent localizedText(String translateKey) {
        return new ArbitraryTextTypeComponent("translate", translateKey);
    }

    /**
     * Create a textual component representing a scoreboard value. The client will see their own score for the specified
     * objective as the text represented by this component. <p> <b>This method is currently guaranteed to throw an
     * {@code UnsupportedOperationException} as it is only supported on snapshot clients.</b> </p>
     *
     * @param scoreboardObjective The name of the objective for which to display the score.
     * @return The text component representing the specified scoreboard score (for the viewing player), or {@code null}
     * if an error occurs during JSON serialization.
     */
    public static TextualComponent objectiveScore(String scoreboardObjective) {
        return objectiveScore("*", scoreboardObjective);
    }

    /**
     * Create a textual component representing a scoreboard value. The client will see the score of the specified player
     * for the specified objective as the text represented by this component. <p> <b>This method is currently guaranteed
     * to throw an {@code UnsupportedOperationException} as it is only supported on snapshot clients.</b> </p>
     *
     * @param playerName          The name of the player whos score will be shown. If this string represents the
     *                            single-character sequence "*", the viewing player's score will be displayed. Standard
     *                            minecraft selectors (@a, @p, etc) are <em>not</em> supported.
     * @param scoreboardObjective The name of the objective for which to display the score.
     * @return The text component representing the specified scoreboard score for the specified player, or {@code null}
     * if an error occurs during JSON serialization.
     */
    public static TextualComponent objectiveScore(String playerName, String scoreboardObjective) {
        return new ComplexTextTypeComponent("score", ImmutableMap.<String, String>builder()
                .put("name", playerName)
                .put("objective", scoreboardObjective)
                .build());
    }

    /**
     * Create a textual component representing a player name, retrievable by using a standard minecraft selector. The
     * client will see the players or entities captured by the specified selector as the text represented by this
     * component. <p> <b>This method is currently guaranteed to throw an {@code UnsupportedOperationException} as it is
     * only supported on snapshot clients.</b> </p>
     *
     * @param selector The minecraft player or entity selector which will capture the entities whose string
     *                 representations will be displayed in the place of this text component.
     * @return The text component representing the name of the entities captured by the selector.
     */
    public static TextualComponent selector(String selector) {
        return new ArbitraryTextTypeComponent("selector", selector);
    }

    @Override
    public String toString() {
        return getReadableString();
    }

    /**
     * @return The JSON key used to represent text components of this type.
     */
    public abstract String getKey();

    /**
     * @return A readable String
     */
    public abstract String getReadableString();

    /**
     * Clones a textual component instance. The returned object should not reference this textual component instance,
     * but should maintain the same key and value.
     */
    public abstract TextualComponent copy();

    /**
     * Writes the text data represented by this textual component to the specified JSON writer object.
     * A new object within the writer is not started.
     *
     * @param writer The object to which to write the JSON data.
     * @throws IOException If an error occurs while writing to the stream.
     */
    public abstract void writeJson(JsonWriter writer) throws IOException;

    /**
     * Internal class used to represent all types of text components.
     * Exception validating done is on keys and values.
     */
    private static final class ArbitraryTextTypeComponent extends TextualComponent {
        public static ArbitraryTextTypeComponent deserialize(Map<String, String> map) {
            return new ArbitraryTextTypeComponent(map.get("key"), map.get("value"));
        }

        private String key;
        private String value;

        public ArbitraryTextTypeComponent(String key, String value) {
            setKey(key);
            setValue(value);
        }

        @Override
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            Preconditions.checkArgument(key != null && !key.isEmpty(), "The key must be specified.");
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            Preconditions.checkArgument(value != null, "The value must be specified.");
            this.value = value;
        }

        @Override
        public TextualComponent copy() {
            return new ArbitraryTextTypeComponent(getKey(), getValue());
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writer.name(getKey()).value(getValue());
        }

        @Override
        public String getReadableString() {
            return getValue();
        }
    }

    /**
     * Internal class used to represent a text component with a nested JSON value.
     * Exception validating done is on keys and values.
     */
    private static final class ComplexTextTypeComponent extends TextualComponent {
        public static ComplexTextTypeComponent deserialize(Map<String, String> map) {
            String key = null;
            Map<String, String> values = new HashMap<String, String>();
            for (Map.Entry<String, String> valEntry : map.entrySet()) {
                if (valEntry.getKey().equals("key")) {
                    key = valEntry.getValue();
                } else if (valEntry.getKey().startsWith("value.")) {
                    // Strips out the value prefix
                    values.put(valEntry.getKey().substring(6), valEntry.getValue());
                }
            }
            return new ComplexTextTypeComponent(key, values);
        }

        private String key;
        private Map<String, String> value;

        public ComplexTextTypeComponent(String key, Map<String, String> values) {
            setKey(key);
            setValue(values);
        }

        @Override
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            Preconditions.checkArgument(key != null && !key.isEmpty(), "The key must be specified.");
            this.key = key;
        }

        public Map<String, String> getValue() {
            return value;
        }

        public void setValue(Map<String, String> value) {
            Preconditions.checkArgument(value != null, "The value must be specified.");
            this.value = value;
        }

        @Override
        public TextualComponent copy() {
            return new ComplexTextTypeComponent(getKey(), getValue());
        }

        @Override
        public void writeJson(JsonWriter writer) throws IOException {
            writer.name(getKey());
            writer.beginObject();
            for (Map.Entry<String, String> jsonPair : value.entrySet()) {
                writer.name(jsonPair.getKey()).value(jsonPair.getValue());
            }
            writer.endObject();
        }

        @Override
        public String getReadableString() {
            return getKey();
        }
    }
}
