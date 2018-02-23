package org.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.LinkedHashMap;

public class GreetingPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getTasks().create("greeting", GreetingTask.class);
    }

}
