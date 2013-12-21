package org.gradle.sample;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        if (args.length > 0) {
            connector.useInstallation(new File(args[0]));
            if (args.length > 1) {
                connector.useGradleUserHomeDir(new File(args[1]));
            }
        }

        connector.forProjectDirectory(new File("."));

        ProjectConnection connection = connector.connect();
        try {
            // Configure the build
            BuildLauncher launcher = connection.newBuild();
            launcher.forTasks("help");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            launcher.setStandardOutput(outputStream);
            launcher.setStandardError(outputStream);

            // Run the build
            launcher.run();
        } finally {
            // Clean up
            connection.close();
        }
    }
}
