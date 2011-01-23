package org.gradle.sample;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.BuildConnection;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseBuild;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        GradleConnector connector = GradleConnector.newConnector();
        try {
            // Configure the connector and create the connection
            connector.forProjectDirectory(new File("."));
            if (args.length > 0) {
                connector.useInstallation(new File(args[0]));
            }
            BuildConnection connection = connector.connect();

            // Load the Eclipse model for the project
            EclipseBuild build = connection.getModel(EclipseBuild.class);
            System.out.println("Project classpath:");
            for (ExternalDependency externalDependency : build.getRootProject().getClasspath()) {
                System.out.println(externalDependency.getFile().getName());
            }
        } finally {
            // Clean up
            connector.close();
        }
    }
}
