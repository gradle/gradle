package org.gradle.integtests;

import org.gradle.api.TaskAction;
import org.gradle.api.Task;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultTask;
import org.gradle.execution.Dag;

public class BrokenTask extends DefaultTask {
    public BrokenTask(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                throw new RuntimeException("broken action");
            }
        });
    }
}
