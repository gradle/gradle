package org.gradle.api.tasks.diagnostics;

import org.gradle.api.internal.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.TaskAction;
import org.gradle.api.Task;
import org.gradle.execution.Dag;
import org.gradle.configuration.ProjectTasksPrettyPrinter;

/**
 * <p>This task prints out the list of tasks in the project, and its subprojects. It is used when you use the task list
 * command-line option.</p>
 */
public class TaskListTask extends DefaultTask {
    private ProjectTasksPrettyPrinter printer = new ProjectTasksPrettyPrinter();

    public TaskListTask(Project project, String name, Dag tasksGraph) {
        super(project, name, tasksGraph);
        setDagNeutral(true);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                generate();
            }
        });
    }

    public void setPrinter(ProjectTasksPrettyPrinter printer) {
        this.printer = printer;
    }

    public void generate() {
        System.out.print(printer.getPrettyText(getProject().getAllTasks(true)));
    }
}
