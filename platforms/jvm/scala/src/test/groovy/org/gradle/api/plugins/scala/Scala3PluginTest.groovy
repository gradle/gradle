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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.tasks.scala.ScalaTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.MatcherAssert.assertThat

class Scala3PluginTest extends AbstractProjectBuilderSpec {

    def addsCompileTaskForEachSourceSet() {
        when:
        project.pluginManager.apply(ScalaPlugin)
        addScalaLibraryDependency()

        then:
        def task = project.tasks['compileScala']
        SourceSet mainSourceSet = project.sourceSets.main
        task instanceof ScalaCompile
        assertScalaClasspath((ScalaCompile) task, project.configurations[mainSourceSet.compileClasspathConfigurationName])
        task.description == 'Compiles the main Scala source.'
        task.classpath.files as List == [
            *mainSourceSet.compileClasspath.files.asList(),
            mainSourceSet.java.destinationDirectory.get().asFile,
        ]
        task.source as List == mainSourceSet.scala  as List
        task dependsOn(JvmConstants.COMPILE_JAVA_TASK_NAME)

        def testTask = project.tasks['compileTestScala']
        SourceSet testSourceSet = project.sourceSets.test
        testTask instanceof ScalaCompile
        assertScalaClasspath((ScalaCompile) testTask, project.configurations[testSourceSet.compileClasspathConfigurationName])
        testTask.description == 'Compiles the test Scala source.'
        testTask.classpath.files as List == [
            mainSourceSet.java.destinationDirectory.get().asFile,
            mainSourceSet.scala.destinationDirectory.get().asFile,
            mainSourceSet.output.resourcesDir,
            *mainSourceSet.compileClasspath.files.asList(),
            testSourceSet.java.destinationDirectory.get().asFile,
        ]
        testTask.source as List == testSourceSet.scala as List
        testTask dependsOn(JvmConstants.COMPILE_TEST_JAVA_TASK_NAME, JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
    }

    def addsScalaDocTasksToTheProject() {
        when:
        project.pluginManager.apply(ScalaPlugin)
        addScalaLibraryDependency()

        then:
        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        task instanceof ScalaDoc
        assertScalaClasspath((ScalaDoc) task, project.sourceSets.main.compileClasspath)
        task dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        task.destinationDir == project.file("$project.docsDir/scaladoc")
        // This assertion is a little tricky, because `task.source` is an empty list since we didn't compile these files, so we check here if [] == []
        task.source as List  == project.sourceSets.main.output.findAll { it.name.endsWith(".tasty") } as List // We take output of main (with tasty files)
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        task.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    private void addScalaLibraryDependency() {
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
            implementation('org.scala-lang:scala3-library_3:3.4.0')
        }
    }

    private void assertScalaClasspath(ScalaTask task, Configuration basedOnClasspath) {
        // Also tests repeated retrievals via `task.scalaClasspath`, so don't save it in a variable
        assert task.scalaClasspath instanceof Configuration
        assert task.scalaClasspath.name == "scalaClasspathForTask${task.name.capitalize()}"
        assert project.configurations[task.scalaClasspath.name] == task.scalaClasspath
        assert task.scalaClasspath.state == Configuration.State.UNRESOLVED
        assert basedOnClasspath.state == Configuration.State.UNRESOLVED

        // To view the dependencies, `basedOnClasspath` has to get resolved, since the Scala version is inferred from its `files`
        assert task.scalaClasspath.dependencies.any { d ->
            d.group == "org.scala-lang" && d.name == "scala3-compiler_3" && d.version == "3.4.0"
        }
        assert task.scalaClasspath.state == Configuration.State.UNRESOLVED
        assert basedOnClasspath.state == Configuration.State.RESOLVED

        // Looking at `scalaClasspath.files` will finally resolve `scalaClasspath` too
        assert task.scalaClasspath.files.any { f ->
            f.name == 'scala3-compiler_3-3.4.0.jar'
        }
        assert task.scalaClasspath.state == Configuration.State.RESOLVED
    }
}
