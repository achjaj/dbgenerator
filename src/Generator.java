import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public class Generator extends DefaultHandler {
    private HashMap<String, ArrayList<JSONObject>> groups = new HashMap<>();
    private Path dest;
    private boolean unihan;

    public Generator(String writeTo, boolean unihan) throws IOException {
        dest = Path.of(writeTo);
        this.unihan = unihan;

        if (!Files.exists(dest) || !Files.isDirectory(dest))
            Files.createDirectory(dest);
    }

    private String getStringAttribute(String name, Attributes attributes) {
        var str = attributes.getValue(name);
        return str == null ? "<NULL>" : str;
    }

    private String getName(Attributes attributes) {
        String name = attributes.getValue(Tags.XML.NAME);
        if (name == null || name.isEmpty())
            name = getStringAttribute(Tags.XML.ALT_NAME, attributes);

        return name;
    }

    private JSONObject getCharObject(Attributes attributes) {
        JSONObject obj = new JSONObject();

        String value = getStringAttribute(Tags.XML.VALUE, attributes);
        String name = getName(attributes);
        String group = unihan ? "unihan" : getStringAttribute(Tags.XML.GROUP, attributes);
        String block = getStringAttribute(Tags.XML.BLOCK, attributes);
        String emoji = attributes.getValue(Tags.XML.EMOJI);

        obj.put(Tags.JSON.BLOCK, block);
        obj.put(Tags.JSON.GROUP, group);
        obj.put(Tags.JSON.NAME, name);
        obj.put(Tags.JSON.VALUE, value);
        obj.put(Tags.JSON.EMOJI, emoji != null && emoji.toLowerCase().equals("y"));

        return obj;
    }

    private void write(JSONObject obj, Path dest) throws IOException {
        FileWriter writer = new FileWriter(dest.toFile());
        obj.write(writer, 4, 0);
        writer.close();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        if (qName.equals(Tags.XML.QNAME)) {
            JSONObject charObj = getCharObject(attributes);

            String group = charObj.getString(Tags.JSON.GROUP);
            if (groups.containsKey(group)) {
                groups.get(group).add(charObj);
            }
            else {
                ArrayList<JSONObject> chars = new ArrayList<>();
                chars.add(charObj);
                groups.put(group, chars);
            }

        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        groups.forEach((groupName, chars) -> {
            JSONObject group = new JSONObject();
            group.put(Tags.JSON.NAME, groupName);
            group.put(Tags.JSON.CHARS, new JSONArray(chars));

            try {
                write(group, Path.of(dest.toString(), groupName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static class Tags {
        private static class XML {
            private static final String QNAME = "char",
                                        GROUP = "gc",
                                        VALUE = "cp",
                                        NAME = "na",
                                        ALT_NAME = "na1",
                                        BLOCK = "blk",
                                        EMOJI = "Emoji";
        }

        private static class JSON {
            private static final String GROUP = "group",
                                        VALUE = "value",
                                        NAME = "name",
                                        BLOCK = "block",
                                        EMOJI = "emoji",
                                        CHARS = "symbols";
        }

    }
}
