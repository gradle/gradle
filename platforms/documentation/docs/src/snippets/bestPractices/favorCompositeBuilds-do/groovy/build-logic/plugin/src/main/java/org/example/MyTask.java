package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public abstract class MyTask extends DefaultTask {
    @TaskAction
    public void action() {
        System.out.println("Executing MyTask");
    }
}
