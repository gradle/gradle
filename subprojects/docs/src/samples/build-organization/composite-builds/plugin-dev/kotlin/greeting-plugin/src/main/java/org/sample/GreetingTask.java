package org.sample;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class GreetingTask extends DefaultTask {

    @Input
    public abstract Property<String> getWho();

    @TaskAction
    public void greet() {
        System.out.println("Hi " + getWho().get() + "!!!");
    }
}
