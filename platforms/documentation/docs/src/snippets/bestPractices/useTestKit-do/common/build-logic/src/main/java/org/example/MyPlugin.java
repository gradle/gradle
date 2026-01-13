package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import java.time.Instant;

public abstract class MyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        MyExtension extension = project.getExtensions().create("myExtension", MyExtension.class);

        project.getTasks().register("task1", MyTask.class, task -> {
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("output1.txt"));
        });

        project.getTasks().register("task2", MyTask.class, task -> {
            task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("output2.txt"));
        });

        project.getTasks().withType(MyTask.class).configureEach(task -> {
            task.setGroup("Custom Tasks");
            task.getFirstName().convention(extension.getFirstName());
            task.getLastName().convention(extension.getLastName()); // <1>
            task.getGreeting().convention("Hi");
            task.getToday().convention(Instant.now()); // <2>
        });
    }
}
