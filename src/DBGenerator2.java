import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;

public class DBGenerator2 {

    private static final String BASE_NAME = "ucd.%s.flat";
    private static final String BASE_URL = "http://www.unicode.org/Public/UCD/latest/ucdxml/%s.zip";
    private static final Path ZIP_PATH = Paths.get("unicode.zip");
    private static final String XML_NAME = "table.xml";
    private static final String VERSION = "2.0b";
    private static final String META_BODY = "flavour=%s\ndbg_version=%s";

    private static void printHelp() {
        System.err.println("Usage: dbgenerator unihan|nounihan|all [d] [dest]\n" +
                "\td - download only" +
                "\tdest - output directory (./db is default)");
        System.exit(1);
    }

    private static String getSource(String flavour) {
        String name = String.format(BASE_NAME, flavour);
        return String.format(BASE_URL, name);
    }

    private static String getXMLName(String flavour) {
        return String.format(BASE_NAME, flavour) + ".xml";
    }

    private static boolean zipExists() {
        return Files.exists(ZIP_PATH) && Files.isRegularFile(ZIP_PATH);
    }

    private static void download(String source) throws IOException {
        System.out.printf("Downloading '%s' to '%s'\n", source, ZIP_PATH.toString());

        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        long fs = connection.getContentLengthLong();
        long ds = 0;

        FileOutputStream out = new FileOutputStream(ZIP_PATH.toFile());
        BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

        byte[] buffer = new byte[1024];
        int x = 0;
        while ((x = in.read(buffer, 0, 1024)) >= 0) {
            ds += x;

            out.write(buffer, 0, x);

            int progress = (int)(ds/fs * 100);
            System.out.printf("Progress: [%d/%d] %d%%\r", ds, fs, progress);
        }
        System.out.println();

        out.close();
        in.close();
        connection.disconnect();
    }

    private static void unzip(String name) throws IOException {
        System.out.println("Extracting");

        try (var fs = FileSystems.newFileSystem(ZIP_PATH, (ClassLoader) null)) {
            var zipSource = fs.getPath(name);
            Files.copy(zipSource, Paths.get(XML_NAME), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void doIt(boolean downloadOnly, String flavour, String dest) throws IOException, ParserConfigurationException, SAXException {
        if (!zipExists())
            download(getSource(flavour));
        unzip(getXMLName(flavour));

        System.out.println("Generating JSON");
        SAXParserFactory.newInstance().newSAXParser().parse(XML_NAME, new Generator2(dest, flavour.equals("unihan")));
    }

    private static void writeMeta(String dest, String flavour) throws IOException {
        System.out.println("Writing meta information");

        String meta = String.format(META_BODY, flavour, VERSION);
        Files.write(Path.of(dest, "meta"), meta.getBytes(), StandardOpenOption.CREATE);
    }

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        if (args.length < 1 || !args[0].matches("all|unihan|nounihan"))
            printHelp();

        boolean dOnly = args.length >= 2 && args[1].equals("d");

        String dest = (args.length < 2 || (dOnly && args.length == 2)) ? "db" : (dOnly ? args[2] : args[1]);
        doIt(dOnly, args[0], dest);
        writeMeta(dest, args[0]);
    }


}
