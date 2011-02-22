package org.gradle.sample;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File("."));
        if (args.length > 0) {
            connector.useInstallation(new File(args[0]));
        }

        ProjectConnection connection = connector.connect();
        try {
            // Load the Eclipse model for the project
            EclipseProject project = connection.getModel(EclipseProject.class);
            System.out.println("Project classpath:");
            for (ExternalDependency externalDependency : project.getClasspath()) {
                System.out.println(externalDependency.getFile().getName());
            }
        } finally {
            // Clean up
            connection.close();
        }
    }
}
