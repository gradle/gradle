package org.gradle.integtests;

import org.gradle.api.TaskAction;
import org.gradle.api.Task;
import org.gradle.api.Project;
import org.gradle.api.DefaultTask;

public class BrokenTask extends DefaultTask {
    public BrokenTask(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                throw new RuntimeException("broken action");
            }
        });
    }
}
