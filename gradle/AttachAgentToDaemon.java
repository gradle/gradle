import java.time.format.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

public class AttachAgentToDaemon {
    private static final File AGENT_JAR_FILE = new File("C:\\tcagent1\\file-leak-detector-1.14-SNAPSHOT-gradle.jar");

    public static void main(String[] args) throws Exception {
        if (!AGENT_JAR_FILE.isFile()) {
            System.err.println(AGENT_JAR_FILE.getAbsolutePath() + " not found, skip.");
            return;
        }

        Properties gradleProperties = new Properties();
        gradleProperties.load(new FileInputStream(new File("gradle.properties")));

        String oldJvmArgs = (String) Optional.ofNullable(gradleProperties.get("org.gradle.jvmargs")).orElseThrow(IllegalStateException::new);
        String newJvmArgs = String.format("-javaagent:%s=gradleRootDir=%s,excludes=%s %s", AGENT_JAR_FILE.getAbsolutePath(), new File(".").getCanonicalPath(), getGradleUserHomeDir(), oldJvmArgs);

        System.out.println("New jvm args: " + newJvmArgs);

        gradleProperties.put("org.gradle.jvmargs", newJvmArgs);
        gradleProperties.store(new FileOutputStream(new File("gradle.properties")), "");
        gradleProperties.store(new FileOutputStream(new File("build-logic/gradle.properties")), "");
    }

    private static String getGradleUserHomeDir() {
        return new File(System.getProperty("user.home"), ".gradle").getAbsolutePath();
    }
}
