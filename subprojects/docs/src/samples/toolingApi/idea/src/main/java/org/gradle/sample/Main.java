package org.gradle.sample;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.File;

//import org.gradle.tooling.model.idea.IdeaDependency;
//import org.gradle.tooling.internal.idea.DefaultIdeaModule;

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
            IdeaProject project = connection.getModel(IdeaProject.class);

            System.out.println("Project: " + project.getName());

            IdeaModule module = project.getChildren().getAt(0);
            System.out.println("Module: " + module.getName());
            
            /*
            System.out.println("Source directories:");
            for (File srcDir : module.getSourceDirectories()) {
                System.out.println(srcDir);
            }
            
            System.out.println("Module dependencies:");
            for (IdeaDependency dependency : module.getModuleDependencies()) {
                System.out.println(dependency);
            }
            
            System.out.println("Library dependencies:");
            for (IdeaDependency dependency : module.getLibraryDependencies()) {
                System.out.println(dependency);
            }
            */
        } finally {
            // Clean up
            connection.close();
        }
    }
}
