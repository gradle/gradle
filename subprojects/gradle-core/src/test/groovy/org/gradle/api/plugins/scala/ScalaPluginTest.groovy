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
package org.gradle.api.plugins.scala

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class ScalaPluginTest {

    private final Project project = HelperUtil.createRootProject()
    private final ScalaPlugin scalaPlugin = new ScalaPlugin()

    @Test public void appliesTheJavaPluginToTheProject() {
        scalaPlugin.use(project, project.getPlugins())
        assertTrue(project.getPlugins().hasPlugin(JavaPlugin))
    }

    @Test public void addsScalaToolsConfigurationToTheProject() {
        scalaPlugin.use(project, project.getPlugins())
        def configuration = project.configurations.getByName(ScalaPlugin.SCALA_TOOLS_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void addsScalaCompileTasksToTheProject() {
        scalaPlugin.use(project, project.getPlugins())

        def task = project.tasks[JavaPlugin.COMPILE_TASK_NAME]
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.srcDirs as Object[]))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.java.source.main.classesDir))
        assertThat(task.scalaSrcDirs, hasItems(project.convention.plugins.scala.scalaSrcDirs as Object[]))

        task = project.tasks[JavaPlugin.COMPILE_TESTS_TASK_NAME]
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.testSrcDirs as Object[]))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.java.source.test.classesDir))
        assertThat(task.scalaSrcDirs, hasItems(project.convention.plugins.scala.scalaTestSrcDirs as Object[]))
    }

    @Test public void configuresCompileTasksDefinedByTheBuildScript() {
        scalaPlugin.use(project, project.getPlugins())

        def task = project.createTask('otherCompile', type: ScalaCompile)
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.srcDirs as Object[]))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.java.source.main.classesDir))
        assertThat(task.scalaSrcDirs, hasItems(project.convention.plugins.scala.scalaSrcDirs as Object[]))
    }

    @Test public void addsScalaDocTasksToTheProject() {
        scalaPlugin.use(project, project.getPlugins())

        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        assertThat(task, instanceOf(ScalaDoc.class))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.scala.scalaDocDir))
        assertThat(task.scalaSrcDirs, hasItems(project.convention.plugins.scala.scalaSrcDirs as Object[]))
    }

    @Test public void configuresScalaDocTasksDefinedByTheBuildScript() {
        scalaPlugin.use(project, project.getPlugins())

        def task = project.createTask('otherScaladoc', type: ScalaDoc)
        assertThat(task.destinationDir, equalTo(project.convention.plugins.scala.scalaDocDir))
        assertThat(task.scalaSrcDirs, hasItems(project.convention.plugins.scala.scalaSrcDirs as Object[]))
    }

}
