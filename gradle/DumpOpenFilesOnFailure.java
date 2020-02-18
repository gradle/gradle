import java.time.format.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;

public class DumpOpenFiles {
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        if ("SUCCESS".equals(System.getenv("PREV_BUILD_STATUS"))) {
            System.out.println("All previous build steps succeeds, skip.");
            System.exit(0);
        }


        try {
            for (String port : parsePorts()) {
                Future<Void> future = (Future<Void>) threadPool.submit(() -> dumpOpenFilesFor(port));
                future.get(60, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    private static void dumpOpenFilesFor(String port) {
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
