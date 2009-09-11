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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import static org.gradle.api.plugins.JavaPlugin.*;
import org.gradle.api.plugins.ProjectPluginsContainer;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDefine;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.util.WrapUtil;

public class ScalaPlugin implements Plugin {
    // public configurations
    public static final String SCALA_TOOLS_CONFIGURATION_NAME = "scalaTools";

    // tasks
    public static final String SCALA_DOC_TASK_NAME = "scaladoc";
    public static final String SCALA_DEFINE_TASK_NAME = "defineScalaAnt";

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        JavaPlugin javaPlugin = projectPluginsHandler.usePlugin(JavaPlugin.class, project);

        ScalaPluginConvention scalaPluginConvention = new ScalaPluginConvention(project);
        project.getConvention().getPlugins().put("scala", scalaPluginConvention);

        project.getConfigurations().add(SCALA_TOOLS_CONFIGURATION_NAME).setVisible(false).setTransitive(true).
                setDescription("The scala tools libraries to be used for this Scala project.");

        configureDefine(project);
        configureCompile(project, javaPlugin);
        configureCompileTests(project, javaPlugin);
        configureScaladoc(project);
    }

    private void configureDefine(Project project) {
        ScalaDefine define = project.getTasks().add(SCALA_DEFINE_TASK_NAME, ScalaDefine.class);
        define.setClasspath(project.getConfigurations().getByName(SCALA_TOOLS_CONFIGURATION_NAME));
        define.setDescription("Defines the scala ant tasks.");
        addDependsOnProjectDependencies(define, SCALA_TOOLS_CONFIGURATION_NAME);
    }

    private void configureCompile(final Project project, JavaPlugin javaPlugin) {
        project.getTasks().withType(ScalaCompile.class).allTasks(new Action<ScalaCompile>() {
            public void execute(ScalaCompile compile) {
                compile.conventionMapping("scalaSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return scala(convention).getScalaSrcDirs();
                    }
                });
                compile.dependsOn(WrapUtil.toSet(SCALA_DEFINE_TASK_NAME));
            }
        });
        ScalaCompile scalaCompile = project.getTasks().replace(COMPILE_TASK_NAME, ScalaCompile.class);
        javaPlugin.configureForSourceSet(SourceSet.MAIN_SOURCE_SET_NAME, scalaCompile);
        scalaCompile.setDescription("Compiles the Scala source code.");
    }

    private void configureCompileTests(final Project project, JavaPlugin javaPlugin) {
        ScalaCompile compileTests = project.getTasks().replace(COMPILE_TEST_TASK_NAME, ScalaCompile.class);
        javaPlugin.configureForSourceSet(SourceSet.TEST_SOURCE_SET_NAME, compileTests);
        compileTests.setDescription("Compiles the Scala test source code.");
        compileTests.conventionMapping("scalaSrcDirs", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return scala(convention).getScalaTestSrcDirs();
            }
        });
    }

    private void configureScaladoc(final Project project) {
        project.getTasks().withType(ScalaDoc.class).allTasks(new Action<ScalaDoc>() {
            public void execute(ScalaDoc scalaDoc) {
                scalaDoc.setClasspath(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                scalaDoc.setDescription("Generates scala doc.");
                scalaDoc.conventionMapping("scalaSrcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return scala(convention).getScalaSrcDirs();
                    }
                });
                scalaDoc.conventionMapping("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return scala(convention).getScalaDocDir();
                    }
                });
                scalaDoc.dependsOn(WrapUtil.toSet(SCALA_DEFINE_TASK_NAME));
            }
        });
        project.getTasks().add(SCALA_DOC_TASK_NAME, ScalaDoc.class).setDescription("Generates scaladoc for the source code.");
    }

    private void addDependsOnProjectDependencies(final Task task, String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getBuildDependencies());
    }

    private static ScalaPluginConvention scala(Convention convention) {
        return (ScalaPluginConvention) convention.getPlugins().get("scala");
    }

}
