package org.gradle.api.tasks.diagnostics;

import org.gradle.api.internal.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.TaskAction;
import org.gradle.api.Task;

import java.util.Map;
import java.util.TreeMap;

/**
 * This task prints out the properties of a project, sub-projects, and tasks. This task is used when you execute
 * the property list command-line option.
 */
public class PropertyListTask extends DefaultTask {
    public PropertyListTask(Project project, String name) {
        super(project, name);
        setDagNeutral(true);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    public void generate() {
        System.out.println("==> property list for " + getProject());
        Map<String, Object> properties = new TreeMap<String, Object>(getProject().getProperties());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            System.out.println(String.format("    %s: %s", entry.getKey(), entry.getValue()));
        }
    }
}
