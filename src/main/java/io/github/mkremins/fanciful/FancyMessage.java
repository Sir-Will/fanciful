package io.github.mkremins.fanciful;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a formattable message. Such messages can use elements such as colors, formatting codes, hover and click
 * data, and other features provided by the vanilla Minecraft <a href="http://minecraft.gamepedia.com/Tellraw#Raw_JSON_Text">JSON
 * message formatter</a>. This class allows plugins to emulate the functionality of the vanilla Minecraft <a
 * href="http://minecraft.gamepedia.com/Commands#tellraw">tellraw command</a>. <p> This class follows the builder
 * pattern, allowing for method chaining. It is set up such that invocations of property-setting methods will affect the
 * current editing component, and a call to {@link #then()} or {@link #then(String)} will append a new editing component
 * to the end of the message, optionally initializing it with text. Further property-setting method calls will affect
 * that editing component. </p>
 */
public class FancyMessage implements JsonRepresentedObject, Iterable<MessagePart> {
    private static final Pattern URL_PATTERN = Pattern.compile("^(?:(https?)://)?([-\\w_\\.]{2,}\\.[a-z]{2,4})(/\\S*)?$");
    private static final JsonParser STRING_PARSER = new JsonParser();

    public static FancyMessage fromJson(String json) {
        JsonObject data = STRING_PARSER.parse(json).getAsJsonObject();
        JsonArray extra = data.getAsJsonArray("extra"); // Get the extra component

        FancyMessage ret = new FancyMessage();
        ret.messageParts.clear();

        for (JsonElement mPrt : extra) {
            MessagePart component = new MessagePart();
            JsonObject messagePart = mPrt.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : messagePart.entrySet()) {

                if (TextualComponent.isTextKey(entry.getKey())) {
                    // The map mimics the YAML serialization, which has a "key" field and one or more "value" fields
                    Map<String, String> map = new HashMap<>();
                    map.put("key", entry.getKey());

                    if (entry.getValue().isJsonPrimitive()) {
                        map.put("value", entry.getValue().getAsString());
                    } else {
                        for (Map.Entry<String, JsonElement> e : entry.getValue().getAsJsonObject().entrySet()) {
                            map.put("value." + e.getKey(), e.getValue().getAsString());
                        }
                    }
                    component.text = TextualComponent.deserialize(map);

                } else if (MessagePart.STYLES_TO_NAMES.inverse().containsKey(entry.getKey())) {
                    if (entry.getValue().getAsBoolean()) {
                        component.styles.add(MessagePart.STYLES_TO_NAMES.inverse().get(entry.getKey()));
                    }

                } else if (entry.getKey().equals("color")) {
                    component.color = ChatColor.valueOf(entry.getValue().getAsString().toUpperCase());

                } else if (entry.getKey().equals("clickEvent")) {
                    JsonObject object = entry.getValue().getAsJsonObject();
                    component.clickActionName = object.get("action").getAsString();
                    component.clickActionData = object.get("value").getAsString();

                } else if (entry.getKey().equals("hoverEvent")) {
                    JsonObject object = entry.getValue().getAsJsonObject();
                    component.hoverActionName = object.get("action").getAsString();
                    if (object.get("value").isJsonPrimitive()) {
                        // Assume string
                        component.hoverActionData = new JsonString(object.get("value").getAsString());
                    } else {
                        // Assume composite type
                        // The only composite type we currently store is another FancyMessage
                        // Therefore, recursion time!
                        component.hoverActionData = fromJson(object.get("value").toString() /* This should properly serialize the JSON object as a JSON string */);
                    }

                } else if (entry.getKey().equals("insertion")) {
                    component.insertionData = entry.getValue().getAsString();

                } else if (entry.getKey().equals("with")) {
                    for (JsonElement object : entry.getValue().getAsJsonArray()) {
                        if (object.isJsonPrimitive()) {
                            component.translationReplacements.add(new JsonString(object.getAsString()));
                        } else {
                            // Only composite type stored in this array is - again - FancyMessages
                            // Recurse within this function to parse this as a translation replacement
                            component.translationReplacements.add(fromJson(object.toString()));
                        }
                    }
                }
            }
            ret.messageParts.add(component);
        }
        return ret;
    }

    public static FancyMessage fromLegacyText(String message) {
        List<MessagePart> components = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        MessagePart component = new MessagePart();
        Matcher matcher = URL_PATTERN.matcher( message );

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == ChatColor.COLOR_CHAR) {
                i++;
                c = message.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    c += 32;
                }
                ChatColor format = ChatColor.getByChar(c);
                if (format == null) {
                    continue;
                }

                // copy style from previous component
                if (builder.length() > 0) {
                    MessagePart old = component;
                    component = old.copy();
                    old.text = TextualComponent.rawText(builder.toString());
                    builder = new StringBuilder();
                    components.add(old);
                }

                switch (format) {
                    case BOLD:
                    case ITALIC:
                    case UNDERLINE:
                    case STRIKETHROUGH:
                    case MAGIC:
                        component.styles.add(format);
                        break;
                    case RESET:
                        format = ChatColor.WHITE;
                    default:
                        component = new MessagePart();
                        component.color = format;
                        break;
                }
                continue;
            }

            int pos = message.indexOf(' ', i);
            if (pos == -1) {
                pos = message.length();
            }
            if (matcher.region(i, pos).find()) { // Web link handling
                if (builder.length() > 0) {
                    MessagePart old = component;
                    component = old.copy();
                    old.text = TextualComponent.rawText(builder.toString());
                    builder = new StringBuilder();
                    components.add(old);
                }

                MessagePart old = component;
                component = old.copy();
                String urlString = message.substring(i, pos);
                component.text = TextualComponent.rawText(urlString);

                component.clickActionName = "open_url";
                component.clickActionData = urlString.startsWith("http") ? urlString : "http://" + urlString;
                components.add(component);

                i += pos - i - 1;
                component = old;
                continue;
            }
            builder.append(c);
        }
        if (builder.length() > 0) {
            component.text = TextualComponent.rawText(builder.toString());
            components.add(component);
        }

        // The client will crash if the array is empty
        if (components.isEmpty()) {
            components.add(new MessagePart(TextualComponent.rawText("")));
        }

        return new FancyMessage(components);
    }

    private List<MessagePart> messageParts;
    private String jsonString;
    private boolean dirty;

    private FancyMessage(List<MessagePart> parts) {
        this.messageParts = parts;
        jsonString = null;
        dirty = false;
    }

    public FancyMessage(String firstPartText) {
        this(TextualComponent.rawText(firstPartText));
    }

    public FancyMessage(TextualComponent firstPartText) {
        messageParts = new ArrayList<>();
        messageParts.add(new MessagePart(firstPartText));
        jsonString = null;
        dirty = false;
    }

    public FancyMessage() {
        this((TextualComponent) null);
    }

    @Override
    public FancyMessage copy() {
        FancyMessage instance = new FancyMessage();
        instance.messageParts = new ArrayList<>(messageParts.size());
        for (int i = 0; i < messageParts.size(); i++) {
            instance.messageParts.add(i, messageParts.get(i).copy());
        }
        instance.dirty = false;
        instance.jsonString = null;
        return instance;
    }

    public FancyMessage apply(Consumer<FancyMessage> consumer) {
        consumer.accept(this);
        return this;
    }

    public <T> FancyMessage apply(T t, BiConsumer<FancyMessage, T> consumer) {
        consumer.accept(this, t);
        return this;
    }

    /**
     * Sets the text of the current editing component to a value.
     *
     * @param text The new text of the current editing component.
     * @return This builder instance.
     */
    public FancyMessage text(String text) {
        MessagePart latest = latest();
        latest.text = TextualComponent.rawText(text);
        dirty = true;
        return this;
    }

    /**
     * Sets the text of the current editing component to a value.
     *
     * @param text The new text of the current editing component.
     * @return This builder instance.
     */
    public FancyMessage text(TextualComponent text) {
        MessagePart latest = latest();
        latest.text = text;
        dirty = true;
        return this;
    }

    /**
     * Sets the color of the current editing component to a value.
     *
     * @param color The new color of the current editing component.
     * @return This builder instance.
     * @throws IllegalArgumentException If the specified {@code ChatColor} enumeration value is not a color (but a
     *                                  format value).
     */
    public FancyMessage color(ChatColor color) {
        if (!color.isColor()) {
            throw new IllegalArgumentException(color.name() + " is not a color");
        }
        latest().color = color;
        dirty = true;
        return this;
    }

    /**
     * Sets the stylization of the current editing component.
     *
     * @param styles The array of styles to apply to the editing component.
     * @return This builder instance.
     * @throws IllegalArgumentException If any of the enumeration values in the array do not represent formatters.
     */
    public FancyMessage style(ChatColor... styles) {
        for (final ChatColor style : styles) {
            if (!style.isFormat()) {
                throw new IllegalArgumentException(style.name() + " is not a style");
            }
        }
        latest().styles.addAll(Arrays.asList(styles));
        dirty = true;
        return this;
    }

    /**
     * Set the behavior of the current editing component to instruct the client to open a file on the client side
     * filesystem when the currently edited part of the {@code FancyMessage} is clicked.
     *
     * @param path The path of the file on the client filesystem.
     * @return This builder instance.
     */
    public FancyMessage file(String path) {
        onClick("open_file", path);
        return this;
    }

    /**
     * Set the behavior of the current editing component to instruct the client to open a webpage in the client's web
     * browser when the currently edited part of the {@code FancyMessage} is clicked.
     *
     * @param url The URL of the page to open when the link is clicked.
     * @return This builder instance.
     */
    public FancyMessage link(String url) {
        onClick("open_url", url);
        return this;
    }

    /**
     * Set the behavior of the current editing component to instruct the client to replace the chat input box content
     * with the specified string when the currently edited part of the {@code FancyMessage} is clicked. The client will
     * not immediately send the command to the server to be executed unless the client player submits the command/chat
     * message, usually with the enter key.
     *
     * @param command The text to display in the chat bar of the client.
     * @return This builder instance.
     */
    public FancyMessage suggest(String command) {
        onClick("suggest_command", command);
        return this;
    }

    /**
     * Set the behavior of the current editing component to instruct the client to append the chat input box content
     * with the specified string when the currently edited part of the {@code FancyMessage} is SHIFT-CLICKED. The client
     * will not immediately send the command to the server to be executed unless the client player submits the
     * command/chat message, usually with the enter key.
     *
     * @param command The text to append to the chat bar of the client.
     * @return This builder instance.
     */
    public FancyMessage insert(String command) {
        latest().insertionData = command;
        dirty = true;
        return this;
    }

    /**
     * Set the behavior of the current editing component to instruct the client to send the specified string to the
     * server as a chat message when the currently edited part of the {@code FancyMessage} is clicked. The client
     * <b>will</b> immediately send the command to the server to be executed when the editing component is clicked.
     *
     * @param command The text to display in the chat bar of the client.
     * @return This builder instance.
     */
    public FancyMessage command(String command) {
        onClick("run_command", command);
        return this;
    }

    /**
     * Set the behavior of the current editing component to display information about an achievement when the client
     * hovers over the text. <p>Tooltips do not inherit display characteristics, such as color and styles, from the
     * message component on which they are applied.</p>
     *
     * @param name The name of the achievement to display, excluding the "achievement." prefix.
     * @return This builder instance.
     */
    public FancyMessage achievementTooltip(String name) {
        onHover("show_achievement", new JsonString("achievement." + name));
        return this;
    }

    /**
     * Set the behavior of the current editing component to display raw text when the client hovers over the text.
     * <p>Tooltips do not inherit display characteristics, such as color and styles, from the message component on which
     * they are applied.</p>
     *
     * @param text The text, which supports newlines, which will be displayed to the client upon hovering.
     * @return This builder instance.
     */
    public FancyMessage tooltip(String text) {
        onHover("show_text", new JsonString(text));
        return this;
    }

    /**
     * Set the behavior of the current editing component to display raw text when the client hovers over the text.
     * <p>Tooltips do not inherit display characteristics, such as color and styles, from the message component on which
     * they are applied.</p>
     *
     * @param lines The lines of text which will be displayed to the client upon hovering. The iteration order of this
     *              object will be the order in which the lines of the tooltip are created.
     * @return This builder instance.
     */
    public FancyMessage tooltip(Iterable<String> lines) {
        tooltip(ArrayWrapper.toArray(lines, String.class));
        return this;
    }

    /**
     * Set the behavior of the current editing component to display raw text when the client hovers over the text.
     * <p>Tooltips do not inherit display characteristics, such as color and styles, from the message component on which
     * they are applied.</p>
     *
     * @param lines The lines of text which will be displayed to the client upon hovering.
     * @return This builder instance.
     */
    public FancyMessage tooltip(String... lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            builder.append(lines[i]);
            if (i != lines.length - 1) {
                builder.append('\n');
            }
        }
        tooltip(builder.toString());
        return this;
    }

    /**
     * Set the behavior of the current editing component to display formatted text when the client hovers over the text.
     * <p>Tooltips do not inherit display characteristics, such as color and styles, from the message component on which
     * they are applied.</p>
     *
     * @param text The formatted text which will be displayed to the client upon hovering.
     * @return This builder instance.
     */
    public FancyMessage formattedTooltip(FancyMessage text) {
        for (MessagePart component : text.messageParts) {
            if (component.clickActionData != null && component.clickActionName != null) {
                throw new IllegalArgumentException("The tooltip text cannot have click data.");
            } else if (component.hoverActionData != null && component.hoverActionName != null) {
                throw new IllegalArgumentException("The tooltip text cannot have a tooltip.");
            }
        }
        onHover("show_text", text);
        return this;
    }

    /**
     * Set the behavior of the current editing component to display the specified lines of formatted text when the
     * client hovers over the text. <p>Tooltips do not inherit display characteristics, such as color and styles, from
     * the message component on which they are applied.</p>
     *
     * @param lines The lines of formatted text which will be displayed to the client upon hovering.
     * @return This builder instance.
     */
    public FancyMessage formattedTooltip(FancyMessage... lines) {
        if (lines.length < 1) {
            onHover(null, null); // Clear tooltip
            return this;
        }

        FancyMessage result = new FancyMessage();
        result.messageParts.clear(); // Remove the one existing text component that exists by default, which destabilizes the object

        for (int i = 0; i < lines.length; i++) {
            for (MessagePart component : lines[i]) {
                if (component.clickActionData != null && component.clickActionName != null) {
                    throw new IllegalArgumentException("The tooltip text cannot have click data.");
                } else if (component.hoverActionData != null && component.hoverActionName != null) {
                    throw new IllegalArgumentException("The tooltip text cannot have a tooltip.");
                }
                if (component.hasText()) {
                    result.messageParts.add(component.copy());
                }
            }
            if (i != lines.length - 1) {
                result.messageParts.add(new MessagePart(TextualComponent.rawText("\n")));
            }
        }
        return formattedTooltip(result.messageParts.isEmpty() ? null : result); // Throws NPE if size is 0, intended
    }

    /**
     * Set the behavior of the current editing component to display the specified lines of formatted text when the
     * client hovers over the text. <p>Tooltips do not inherit display characteristics, such as color and styles, from
     * the message component on which they are applied.</p>
     *
     * @param lines The lines of text which will be displayed to the client upon hovering. The iteration order of this
     *              object will be the order in which the lines of the tooltip are created.
     * @return This builder instance.
     */
    public FancyMessage formattedTooltip(final Iterable<FancyMessage> lines) {
        return formattedTooltip(ArrayWrapper.toArray(lines, FancyMessage.class));
    }

    /**
     * Terminate construction of the current editing component, and begin construction of a new message component. After
     * a successful call to this method, all setter methods will refer to a new message component, created as a result
     * of the call to this method.
     *
     * @param text The text which will populate the new message component.
     * @return This builder instance.
     */
    public FancyMessage then(String text) {
        return then(TextualComponent.rawText(text));
    }

    /**
     * Terminate construction of the current editing component, and begin construction of a new message component. After
     * a successful call to this method, all setter methods will refer to a new message component, created as a result
     * of the call to this method.
     *
     * @param text The text which will populate the new message component.
     * @return This builder instance.
     */
    public FancyMessage then(TextualComponent text) {
        if (!latest().hasText()) {
            throw new IllegalStateException("previous message part has no text");
        }
        messageParts.add(new MessagePart(text));
        dirty = true;
        return this;
    }

    /**
     * Terminate construction of the current editing component, and begin construction of a new message component. After
     * a successful call to this method, all setter methods will refer to a new message component, created as a result
     * of the call to this method.
     *
     * @return This builder instance.
     */
    public FancyMessage then() {
        if (!latest().hasText()) {
            throw new IllegalStateException("previous message part has no text");
        }
        messageParts.add(new MessagePart());
        dirty = true;
        return this;
    }

    @Override
    public void writeJson(JsonWriter writer) throws IOException {
        if (messageParts.size() == 1) {
            latest().writeJson(writer);
        } else {
            writer.beginObject().name("text").value("").name("extra").beginArray();
            for (final MessagePart part : this) {
                part.writeJson(writer);
            }
            writer.endArray().endObject();
        }
    }

    public String exportToJson() {
        if (!dirty && jsonString != null) {
            return jsonString;
        }
        StringWriter string = new StringWriter();
        JsonWriter json = new JsonWriter(string);
        try {
            writeJson(json);
            json.close();
        } catch (IOException e) {
            throw new RuntimeException("invalid message");
        }
        jsonString = string.toString();
        dirty = false;
        return jsonString;
    }

    public String toOldMessageFormat() {
        StringBuilder result = new StringBuilder();
        for (MessagePart part : this) {
            result.append(part.color == null ? "" : part.color);
            for (ChatColor formatSpecifier : part.styles) {
                result.append(formatSpecifier);
            }
            result.append(part.text);
        }
        return result.toString();
    }

    private MessagePart latest() {
        return messageParts.get(messageParts.size() - 1);
    }

    private void onClick(final String name, final String data) {
        final MessagePart latest = latest();
        latest.clickActionName = name;
        latest.clickActionData = data;
        dirty = true;
    }

    private void onHover(final String name, final JsonRepresentedObject data) {
        final MessagePart latest = latest();
        latest.hoverActionName = name;
        latest.hoverActionData = data;
        dirty = true;
    }

    /**
     * <b>Internally called method. Not for API consumption.</b>
     */
    public Iterator<MessagePart> iterator() {
        return messageParts.iterator();
    }

}
