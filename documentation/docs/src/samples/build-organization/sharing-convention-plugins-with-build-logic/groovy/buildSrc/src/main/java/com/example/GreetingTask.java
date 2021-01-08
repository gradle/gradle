package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.provider.Property;

public abstract class GreetingTask extends DefaultTask {

    @Input
    public abstract Property<String> getGreeting();

    @TaskAction
    public void greet() {
        System.out.println(getGreeting().get());
    }
}
