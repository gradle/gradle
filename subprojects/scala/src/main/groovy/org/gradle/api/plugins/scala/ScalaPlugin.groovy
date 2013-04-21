/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.scala;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.plugins.JavaBasePlugin

public class ScalaPlugin implements Plugin<Project> {
    // tasks
    public static final String SCALA_DOC_TASK_NAME = "scaladoc";

    public void apply(Project project) {
        project.plugins.apply(ScalaBasePlugin.class);
        project.plugins.apply(JavaPlugin.class);

        configureScaladoc(project);
    }

    private void configureScaladoc(final Project project) {
        project.getTasks().withType(ScalaDoc.class) {ScalaDoc scalaDoc ->
            scalaDoc.conventionMapping.classpath = { project.sourceSets.main.output + project.sourceSets.main.compileClasspath }
            scalaDoc.source = project.sourceSets.main.scala
        }
        ScalaDoc scalaDoc = project.tasks.create(SCALA_DOC_TASK_NAME, ScalaDoc.class)
        scalaDoc.description = "Generates Scaladoc for the main source code.";
        scalaDoc.group = JavaBasePlugin.DOCUMENTATION_GROUP
    }
}
