package com.example.sample;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.test.TestFailureResult;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestSkippedResult;
import org.gradle.tooling.events.test.TestSuccessResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        connector.useInstallation(new File(args[0]));
        connector.forProjectDirectory(new File(args[1]));

        ProjectConnection connection = connector.connect();
        try {
            // Configure the build
            BuildLauncher launcher = connection.newBuild();
            launcher.forTasks("test");
            launcher.setStandardOutput(System.out);
            launcher.setStandardError(System.err);
            launcher.addProgressListener(progressEvent -> {
                if (progressEvent instanceof TestFinishEvent testFinishEvent) {
                    switch(testFinishEvent.getResult()) {
                        case TestFailureResult r -> System.out.println(toEventPath(testFinishEvent.getDescriptor()) + " failed");
                        case TestSkippedResult r -> System.out.println(toEventPath(testFinishEvent.getDescriptor()) + " skipped");
                        case TestSuccessResult r -> System.out.println(toEventPath(testFinishEvent.getDescriptor()) + " succeeded");
                        default -> System.out.println(toEventPath(testFinishEvent.getDescriptor()) + " finished with unknown result");
                    }
                }
            }, Collections.singleton(OperationType.TEST));

            // Run the build
            launcher.run();
        } finally {
            // Clean up
            connection.close();
        }
    }

    private static String toEventPath(OperationDescriptor descriptor) {
        List<String> names = new ArrayList<>();
        OperationDescriptor current = descriptor;
        while (current != null) {
            names.add(current.getDisplayName());
            current = current.getParent();
        }
        Collections.reverse(names);
        return String.join(" > ", names);
    }
}
