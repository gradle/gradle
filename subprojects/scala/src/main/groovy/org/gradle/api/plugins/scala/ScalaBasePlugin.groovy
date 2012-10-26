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
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.tasks.DefaultScalaSourceSet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc

public class ScalaBasePlugin implements Plugin<Project> {
    // public configurations
    public static final String SCALA_TOOLS_CONFIGURATION_NAME = "scalaTools";
    public static final String ZINC_CONFIGURATION_NAME = "zinc";

    public void apply(Project project) {
        JavaBasePlugin javaPlugin = project.plugins.apply(JavaBasePlugin.class);

        project.configurations.add(SCALA_TOOLS_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Scala tools libraries to be used for this Scala project.");
        project.configurations.add(ZINC_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Zinc incremental compiler to be used for this Scala project.");

        configureCompileDefaults(project, javaPlugin)
        configureSourceSetDefaults(project, javaPlugin)
        configureScaladoc(project);
    }

    private void configureSourceSetDefaults(Project project, JavaBasePlugin javaPlugin) {
        project.convention.getPlugin(JavaPluginConvention.class).sourceSets.all { SourceSet sourceSet ->
            sourceSet.convention.plugins.scala = new DefaultScalaSourceSet(sourceSet.displayName, project.fileResolver)
            sourceSet.scala.srcDir { project.file("src/$sourceSet.name/scala")}
            sourceSet.allJava.source(sourceSet.scala)
            sourceSet.allSource.source(sourceSet.scala)
            sourceSet.resources.filter.exclude { FileTreeElement element -> sourceSet.scala.contains(element.file) }

            String taskName = sourceSet.getCompileTaskName('scala')
            ScalaCompile scalaCompile = project.tasks.add(taskName, ScalaCompile.class);
            scalaCompile.dependsOn sourceSet.compileJavaTaskName
            javaPlugin.configureForSourceSet(sourceSet, scalaCompile);
            scalaCompile.description = "Compiles the $sourceSet.scala.";
            scalaCompile.source = sourceSet.scala

            project.tasks[sourceSet.classesTaskName].dependsOn(taskName)
        }
    }

    private void configureCompileDefaults(final Project project, JavaBasePlugin javaPlugin) {
        project.tasks.withType(ScalaCompile.class) { ScalaCompile compile ->
            compile.scalaClasspath = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
            compile.conventionMapping.zincClasspath = {
                def config = project.configurations[ZINC_CONFIGURATION_NAME]
                if (config.dependencies.empty) {
                    project.dependencies {
                        zinc("com.typesafe.zinc:zinc:0.2.0-M2") {
                            exclude module: "ensime-sbt-cmd"
                        }
                    }
                }
                config
            }
            // TODO: Not nice, but scalaCompileOptions can't be convention mapping enabled
            // because then it's no longer serializable. Also, we can't defer setting the
            // default until a ScalaCompile task executes because ScalaCompile tasks need
            // access to the compiler cache files of all other ScalaCompile tasks in the build
            project.gradle.projectsEvaluated {
                if (compile.scalaCompileOptions.compilerCacheFile == null) {
                    compile.scalaCompileOptions.compilerCacheFile = new File("$project.buildDir/tmp/scala/compilerCache/${compile.name}.cache")
                }
            }
        }
    }

    private void configureScaladoc(final Project project) {
        project.getTasks().withType(ScalaDoc.class) {ScalaDoc scalaDoc ->
            scalaDoc.conventionMapping.destinationDir = { project.file("$project.docsDir/scaladoc") }
            scalaDoc.conventionMapping.title = { project.extensions.getByType(ReportingExtension).apiDocTitle }
            scalaDoc.scalaClasspath = project.configurations[SCALA_TOOLS_CONFIGURATION_NAME]
        }
    }
}