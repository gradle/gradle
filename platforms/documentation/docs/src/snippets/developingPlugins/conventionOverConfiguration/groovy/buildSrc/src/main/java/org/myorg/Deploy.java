package org.myorg;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

abstract public class Deploy extends DefaultTask {

    @Input
    abstract public Property<String> getUrl();

    @TaskAction
    public void deploy() {
        System.out.println("Deploying to URL " + getUrl().get());
    }
}
