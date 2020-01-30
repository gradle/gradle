import java.time.format.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

public class DumpOpenFiles {
    public static void main(String[] args) throws Exception {
        String url = "http://localhost:" + parsePort() + "/dump";
        System.out.println("Sending request to " + url);
        try (BufferedReader response = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String line = null;
            while ((line = response.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String parsePort() throws Exception {
        if (new File("port.txt").isFile()) {
            return Files.readAllLines(new File("port.txt").toPath()).get(0).trim();
        } else {
            System.err.println("Port not found, skip");
            System.exit(0);
            return "";
        }
    }
}
