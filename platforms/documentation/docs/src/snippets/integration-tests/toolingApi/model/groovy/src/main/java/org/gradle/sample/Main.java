package org.gradle.sample;

import org.gradle.tooling.*;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.eclipse.*;

import java.io.File;
import java.lang.Object;

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

        try (ProjectConnection connection = connector.connect()) {
            // Load the model for the project
            GradleProject project = connection.getModel(GradleProject.class);
            System.out.println("Project: " + project.getName());
            System.out.println("Tasks:");
            for (Task task : project.getTasks()) {
                System.out.println("    " + task.getName());
            }
        }
    }
}
