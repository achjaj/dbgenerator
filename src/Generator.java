import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

public class Generator extends DefaultHandler {
    private HashMap<String, ArrayList<String>> groups = new HashMap<>();
    private String flavour;
    private String unicodeVersion;
    private boolean description = false;

    private final Connection connection;
    private final Statement statement;


    public Generator(String writeTo, String flavour) throws SQLException {
        this.flavour = flavour;

        connection = DriverManager.getConnection("jdbc:sqlite:" + writeTo);
        statement = connection.createStatement();
    }

    private String getStringAttribute(String name, Attributes attributes) {
        var str = attributes.getValue(name);
        return str == null ? "<NULL>" : str;
    }

    private String getName(Attributes attributes) {
        String name = attributes.getValue(Generator.Tags.NAME);
        if (name == null || name.isEmpty())
            name = getStringAttribute(Generator.Tags.ALT_NAME, attributes);

        return name;
    }

    private String[] getCharObject(Attributes attributes) {
        String[] info = new String[2];

        String value = getStringAttribute(Generator.Tags.VALUE, attributes);
        String name = getName(attributes);
        String group = flavour.equals("unihan") ? "unihan" : getStringAttribute(Generator.Tags.GROUP, attributes);
        String block = getStringAttribute(Generator.Tags.BLOCK, attributes);
        String emojiStr = attributes.getValue(Generator.Tags.EMOJI);

        int emoji;
        if (emojiStr == null)
            emoji = 0;
        else
            emoji = emojiStr.toLowerCase().equals("y") ? 1 : 0;

        info[0] = String.format("INSERT INTO %s(value, name, groupName, block, emoji)" +
                "VALUES('%s', '%s', '%s', '%s', %d)", group, value, name, group, block, emoji);
        info[1] = group;

        return info;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        if (qName.equals(Generator.Tags.QNAME)) {
            String[] charInf = getCharObject(attributes);

            String group = charInf[1];
            if (groups.containsKey(group)) {
                groups.get(group).add(charInf[0]);
            }
            else {
                ArrayList<String> chars = new ArrayList<>();
                chars.add(charInf[0]);
                groups.put(group, chars);
            }

        } else if (qName.equals(Tags.VER_FIELD)) {
            description = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);

        if (qName.equals(Tags.VER_FIELD))
            description = false;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);

        /*int now = start + length;
        double percent = (double)now / ch.length * 100;
        System.out.printf("[%d/%d] %3.0f%%\r", now, ch.length, percent);*/

        if (description) {
            unicodeVersion = new String(ch).substring(start, start + length).split(" ")[1];
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        try {
            write();
            writeMeta();
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private void write() throws SQLException {
        int counter = 0;
        int size = groups.values().stream().mapToInt(ArrayList::size).sum();

        for (String group : groups.keySet()) {
            statement.execute("DROP TABLE IF EXISTS " + group);
            statement.execute("CREATE TABLE " + group + "(value TEXT, name TEXT, groupName TEXT, block TEXT, emoji INTEGER)");

            var cmds = groups.get(group);
            for (String cmd : cmds) {
                counter++;
                System.out.printf("Writing symbol: [%d/%d] %3.0f%%\r", counter, size, (double)counter / size * 100);
                statement.execute(cmd);
            }
        }
        System.out.println("\nDone!");
    }

    private void writeMeta() throws SQLException {
        statement.execute("DROP TABLE IF EXISTS meta");
        statement.execute("CREATE TABLE meta (gVersion TEXT, uVersion TEXT, flavour TEXT)");
        String VERSION = "0.1";
        statement.execute(String.format("INSERT INTO meta(gVersion, uVersion, flavour) VALUES('%s', '%s', '%s')", VERSION, unicodeVersion, flavour));
    }

    private static class Tags {
        private static final String QNAME = "char",
                GROUP = "gc",
                VALUE = "cp",
                NAME = "na",
                ALT_NAME = "na1",
                BLOCK = "blk",
                EMOJI = "Emoji",
                VER_FIELD = "description";
    }
}
