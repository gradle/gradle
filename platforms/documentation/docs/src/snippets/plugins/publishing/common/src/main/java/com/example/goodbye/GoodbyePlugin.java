package com.example.goodbye;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.Task;

public class GoodbyePlugin implements Plugin<Project> {
  public void apply(Project project) {
    Task goodbye = project.getTasks().create("goodbye");
    goodbye.doLast(new Action<Task>() {
      @Override
      public void execute(Task task) {
        task.getLogger().lifecycle("Goodbye!");
      }
    });
  }
}
