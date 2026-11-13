package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Prints a message and produces no cacheable output")
public abstract class HelloTask extends DefaultTask {

    @Input
    public abstract Property<String> getMessage();

    @TaskAction
    public void run() {
        System.out.println(getMessage().get());
    }
}
