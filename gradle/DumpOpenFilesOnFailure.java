import java.time.format.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

public class DumpOpenFiles {
    public static void main(String[] args) throws Exception {
        if ("SUCCESS".equals(System.getenv("PREV_BUILD_STATUS"))) {
            System.out.println("All previous build steps succeeds, skip.");
            System.exit(0);
        }

        for (String port : parsePorts()) {
            String url = "http://localhost:" + port + "/dump";
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
    }

    private static Set<String> parsePorts() throws Exception {
        if (new File("ports.txt").isFile()) {
            return Files.readAllLines(new File("ports.txt").toPath()).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        } else {
            System.err.println("ports.txt not found, skip");
            System.exit(0);
            return Collections.emptySet();
        }
    }
}
