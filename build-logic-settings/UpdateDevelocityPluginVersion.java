import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class UpdateDevelocityPluginVersion {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java build-logic-settings/UpdateDevelocityPluginVersion.java <new-version>");
            System.exit(1);
        }

        String newVersion = args[0];
        String[] files = {
            "build-logic-commons/build-platform/build.gradle.kts",
            "build-logic-settings/settings.gradle.kts",
            "settings.gradle.kts"
        };

        for (String file : files) {
            File f = new File(file);
            if (f.exists()) {
                String content = new String(Files.readAllBytes(f.toPath()));
                content = content.replaceAll("com.gradle:develocity-gradle-plugin:[^\\\"]*\"", "com.gradle:develocity-gradle-plugin:" + newVersion + '"');
                content = content.replaceAll("com.gradle.develocity\"\\).version\\(\"[^\\\"]*\"", "com.gradle.develocity\"\\).version\\(\"" + newVersion + '"');

                Files.write(f.toPath(), content.getBytes());
                System.out.println("Updated " + file + " to version " + newVersion);
            } else {
                System.out.println("File " + file + " not found");
            }
        }

        System.out.println("All files updated successfully.");
    }
}
