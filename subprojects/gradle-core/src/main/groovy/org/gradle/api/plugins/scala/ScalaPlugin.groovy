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
import org.gradle.api.internal.tasks.DefaultScalaSourceSet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ProjectPluginsContainer

import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDefine
import org.gradle.api.tasks.scala.ScalaDoc
import static org.gradle.api.plugins.JavaPlugin.*

public class ScalaPlugin implements Plugin {
    // public configurations
    public static final String SCALA_TOOLS_CONFIGURATION_NAME = "scalaTools";

    // tasks
    public static final String SCALA_DOC_TASK_NAME = "scaladoc";
    public static final String SCALA_DEFINE_TASK_NAME = "defineScalaAnt";

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        JavaPlugin javaPlugin = projectPluginsHandler.usePlugin(JavaPlugin.class, project);

        project.configurations.add(SCALA_TOOLS_CONFIGURATION_NAME).setVisible(false).setTransitive(true).
                setDescription("The Scala tools libraries to be used for this Scala project.");

        configureDefine(project);
        configureCompileDefaults(project, javaPlugin)
        configureSourceSetDefaults(project, javaPlugin)
        configureScaladoc(project);
    }

    private void configureSourceSetDefaults(Project project, JavaPlugin javaPlugin) {
        project.convention.getPlugin(JavaPluginConvention.class).source.allObjects {SourceSet sourceSet ->
            sourceSet.convention.plugins.scala = new DefaultScalaSourceSet(sourceSet.displayName, project.fileResolver)
            sourceSet.scala.srcDir { project.file("src/$sourceSet.name/scala")}
            sourceSet.allJava.add(sourceSet.scala.matching(sourceSet.java.filter))
            sourceSet.allSource.add(sourceSet.scala)
            sourceSet.resources.filter.exclude('**/*.scala')

            String taskName = "${sourceSet.compileTaskName}Scala"
            ScalaCompile scalaCompile = project.tasks.add(taskName, ScalaCompile.class);
            scalaCompile.dependsOn sourceSet.compileJavaTaskName
            javaPlugin.configureForSourceSet(sourceSet, scalaCompile);
            scalaCompile.description = "Compiles the $sourceSet.scala.";
            scalaCompile.conventionMapping.defaultSource = { sourceSet.scala }

            project.tasks[sourceSet.compileTaskName].dependsOn(taskName)
        }
    }

    private void configureDefine(Project project) {
        ScalaDefine define = project.tasks.add(SCALA_DEFINE_TASK_NAME, ScalaDefine.class);
        define.conventionMapping.classpath = { project.configurations[SCALA_TOOLS_CONFIGURATION_NAME] }
        define.description = "Defines the Scala ant tasks.";
    }

    private void configureCompileDefaults(final Project project, JavaPlugin javaPlugin) {
        project.tasks.withType(ScalaCompile.class).allTasks {ScalaCompile compile ->
            compile.dependsOn(SCALA_DEFINE_TASK_NAME)
        }
    }

    private void configureScaladoc(final Project project) {
        project.getTasks().withType(ScalaDoc.class).allTasks {ScalaDoc scalaDoc ->
            scalaDoc.conventionMapping.classpath = { project.source.main.classes + project.source.main.compileClasspath }
            scalaDoc.conventionMapping.defaultSource = { project.source.main.scala }
            scalaDoc.conventionMapping.destinationDir = { project.file("$project.docsDir/scaladoc") }
            scalaDoc.conventionMapping.title = { project.apiDocTitle }
            scalaDoc.dependsOn(SCALA_DEFINE_TASK_NAME)
        }
        project.tasks.add(SCALA_DOC_TASK_NAME, ScalaDoc.class).description = "Generates scaladoc for the source code.";
    }
}
