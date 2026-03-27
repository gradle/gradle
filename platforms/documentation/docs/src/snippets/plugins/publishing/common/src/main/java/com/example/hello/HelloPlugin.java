package com.example.hello;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.Task;

public class HelloPlugin implements Plugin<Project> {
  public void apply(Project project) {
    Task hello = project.getTasks().create("hello");
    hello.doLast(new Action<Task>() {
      @Override
      public void execute(Task task) {
        task.getLogger().lifecycle("Hello!");
      }
    });
  }
}
