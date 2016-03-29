package org.buildSrc;

import org.gradle.api.*;

public class Example implements Plugin<Project> {
   public void apply(Project project) {
      project.getTasks().create("pluginTask");
   }
}
