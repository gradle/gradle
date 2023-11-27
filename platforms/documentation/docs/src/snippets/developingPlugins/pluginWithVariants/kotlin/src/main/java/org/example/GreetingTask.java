package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class GreetingTask extends DefaultTask {
    @TaskAction
    public void greet() {
        System.out.println("Hello from Gradle <7");
    }
}
