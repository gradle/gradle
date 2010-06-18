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

import static org.hamcrest.Matchers.*

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.gradle.util.WrapUtil.*
import static org.junit.Assert.*

public class ScalaBasePluginTest {

    private final Project project = HelperUtil.createRootProject()
    private final ScalaBasePlugin scalaPlugin = new ScalaBasePlugin()

    @Test public void appliesTheJavaPluginToTheProject() {
        scalaPlugin.apply(project)
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin))
    }

    @Test public void addsScalaToolsConfigurationToTheProject() {
        scalaPlugin.apply(project)
        def configuration = project.configurations.getByName(ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test public void addsScalaConventionToNewSourceSet() {
        scalaPlugin.apply(project)

        def sourceSet = project.sourceSets.add('custom')
        assertThat(sourceSet.scala.displayName, equalTo("custom Scala source"))
        assertThat(sourceSet.scala.srcDirs, equalTo(toLinkedSet(project.file("src/custom/scala"))))
    }

    @Test public void addsCompileTaskForNewSourceSet() {
        scalaPlugin.apply(project)

        project.sourceSets.add('custom')
        def task = project.tasks['compileCustomScala']
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Scala source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.custom.compileClasspath))
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.scala))
        assertThat(task, dependsOn('compileCustomJava'))
    }
    
    @Test public void dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        scalaPlugin.apply(project)

        project.sourceSets.add('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomScala')))
    }

    @Test public void configuresCompileTasksDefinedByTheBuildScript() {
        scalaPlugin.apply(project)

        def task = project.createTask('otherCompile', type: ScalaCompile)
        assertThat(task.defaultSource, nullValue())
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task, dependsOn())
    }

    @Test public void configuresScalaDocTasksDefinedByTheBuildScript() {
        scalaPlugin.apply(project)

        def task = project.createTask('otherScaladoc', type: ScalaDoc)
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/scaladoc")))
        assertThat(task.title, equalTo(project.apiDocTitle))
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task, dependsOn())
    }
}