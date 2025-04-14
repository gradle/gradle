package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;

public class GreetingTask extends DefaultTask {

    private String greeting = "hello from GreetingTask";

    @Input
    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    @TaskAction
    public void greet() {
        System.out.println(greeting);
    }
}
