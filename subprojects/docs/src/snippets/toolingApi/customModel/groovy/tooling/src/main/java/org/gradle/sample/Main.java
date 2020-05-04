package org.gradle.sample;

import org.gradle.tooling.*;
import org.gradle.sample.plugin.CustomModel;

import java.io.File;
import java.lang.Object;
import java.lang.String;
import java.lang.System;

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

        connector.forProjectDirectory(new File("../sampleBuild"));

        ProjectConnection connection = connector.connect();
        try {
            // Load the custom model for the project
            CustomModel model = connection.getModel(CustomModel.class);
            System.out.println("Project: " + model.getName());
            System.out.println("Tasks: ");
            for (String task : model.getTasks()) {
                System.out.println("   " + task);
            }
        } finally {
            // Clean up
            connection.close();
        }
    }
}
