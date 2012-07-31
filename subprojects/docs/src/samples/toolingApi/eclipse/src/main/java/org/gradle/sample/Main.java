package org.gradle.sample;

import org.gradle.tooling.*;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.eclipse.*;

import java.io.File;
import java.lang.String;
import java.lang.System;

public class Main {
    public static void main(String[] args) {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File("."));

        // Workaround http://issues.gradle.org/browse/GRADLE-2405
        String gradleUserHomeDir = System.getProperty("gradle.user.home");
        if (gradleUserHomeDir != null && gradleUserHomeDir.length() > 0) {
            connector.useGradleUserHomeDir(new File(gradleUserHomeDir));
        }

        if (args.length > 0) {
            connector.useInstallation(new File(args[0]));
        }

        ProjectConnection connection = connector.connect();
        try {
            // Load the Eclipse model for the project
            EclipseProject project = connection.getModel(EclipseProject.class);
            System.out.println("Project: " + project.getName());
            System.out.println("Project directory: " + project.getProjectDirectory());
            System.out.println("Source directories:");
            for (EclipseSourceDirectory srcDir : project.getSourceDirectories()) {
                System.out.println(srcDir.getPath());
            }
            System.out.println("Project classpath:");
            for (ExternalDependency externalDependency : project.getClasspath()) {
                System.out.println(externalDependency.getFile().getName());
            }
            System.out.println("Associated gradle project:");
            System.out.println(project.getGradleProject());
        } finally {
            // Clean up
            connection.close();
        }
    }
}
