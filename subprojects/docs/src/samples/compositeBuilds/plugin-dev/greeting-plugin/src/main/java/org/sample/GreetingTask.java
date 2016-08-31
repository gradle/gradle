package org.sample;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class GreetingTask extends DefaultTask {
    @Input
    public String who = "mate";

    @TaskAction
    public void greet() {
        System.out.println("Hi " + who + "!!!");
    }
}
