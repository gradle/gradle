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


import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.tasks.scala.ScalaTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class ScalaPluginTest extends AbstractProjectBuilderSpec {

    def appliesTheJavaPluginToTheProject() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        then:
        project.getPlugins().hasPlugin(JavaPlugin)
    }

    def addsScalaConventionToEachSourceSetAndAppliesMappings() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        then:
        def sourceSet = project.sourceSets.main
        sourceSet.scala.displayName == "main Scala source"
        sourceSet.scala.srcDirs == toLinkedSet(project.file("src/main/scala"))

        def testSourceSet = project.sourceSets.test
        testSourceSet.scala.displayName == "test Scala source"
        testSourceSet.scala.srcDirs ==  toLinkedSet(project.file("src/test/scala"))
    }

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

    def "compile dependency to java compilation can be turned off by changing the compile task classpath"() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        then:
        def task = project.tasks['compileScala']
        task.setClasspath(project.sourceSets.main.compileClasspath)

        SourceSet mainSourceSet = project.sourceSets.main
        task  instanceof ScalaCompile
        task.classpath.files as List == []
        task.source as List == mainSourceSet.scala  as List
        task not(dependsOn(JvmConstants.COMPILE_JAVA_TASK_NAME))

        def testTask = project.tasks['compileTestScala']
        testTask.setClasspath(project.sourceSets.test.compileClasspath)

        def testSourceSet = project.sourceSets.test
        testTask  instanceof ScalaCompile
        testTask.classpath.files as List == [
            mainSourceSet.java.destinationDirectory.get().asFile,
            mainSourceSet.scala.destinationDirectory.get().asFile,
            mainSourceSet.output.resourcesDir
        ]
        testTask.source as List == testSourceSet.scala as List
        testTask dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        testTask not(dependsOn(JvmConstants.COMPILE_TEST_JAVA_TASK_NAME))
    }

    def dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        then:
        def task = project.tasks[JvmConstants.CLASSES_TASK_NAME]
        task dependsOn('compileScala', JvmConstants.COMPILE_JAVA_TASK_NAME, JvmConstants.PROCESS_RESOURCES_TASK_NAME)

        def testTask = project.tasks[JvmConstants.TEST_CLASSES_TASK_NAME]
        testTask dependsOn('compileTestScala', JvmConstants.COMPILE_TEST_JAVA_TASK_NAME, 'processTestResources')
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
        task.source as List == project.sourceSets.main.scala as List // We take sources of main
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        task.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    def configuresScalaDocTasksDefinedByTheBuildScript() {
        when:
        project.pluginManager.apply(ScalaPlugin)

        then:
        def task = project.task('otherScaladoc', type: ScalaDoc)
        task dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
    }

    def compileScalaTaskFailsIfScalaLibraryDependencyIsMissing() {
        when:
        project.pluginManager.apply(ScalaPlugin)
        ScalaCompile task = project.tasks['compileScala']
        task.compile()

        then:
        Exception e = thrown()
        assertScalaLibraryDependencyIsMissingException(e, task.name)
    }

    def compileTestScalaTaskFailsIfScalaLibraryDependencyIsMissing() {
        when:
        project.pluginManager.apply(ScalaPlugin)
        ScalaCompile task = project.tasks['compileTestScala']
        task.compile()

        then:
        Exception e = thrown()
        assertScalaLibraryDependencyIsMissingException(e, task.name)
    }

    def scalaDocTaskFailsIfScalaLibraryDependencyIsMissing() {
        when:
        project.pluginManager.apply(ScalaPlugin)
        ScalaDoc task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        task.generate()

        then:
        Exception e = thrown()
        assertScalaLibraryDependencyIsMissingException(e, task.name)
    }

    private void addScalaLibraryDependency() {
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
            implementation('org.scala-lang:scala-library:2.13.12')
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
            d.group == "org.scala-lang" && d.name == "scala-compiler" && d.version == "2.13.12"
        }
        assert task.scalaClasspath.state == Configuration.State.UNRESOLVED
        assert basedOnClasspath.state == Configuration.State.RESOLVED

        // Looking at `scalaClasspath.files` will finally resolve `scalaClasspath` too
        assert task.scalaClasspath.files.any { f ->
            f.name == 'scala-compiler-2.13.12.jar'
        }
        assert task.scalaClasspath.state == Configuration.State.RESOLVED
    }

    private void assertScalaLibraryDependencyIsMissingException(Exception e, String taskName) {
        assert e instanceof InvalidUserDataException
        assert e.message == "'${taskName}.scalaClasspath' must not be empty. If a Scala library dependency is provided, the 'scala-base' plugin will attempt to configure 'scalaClasspath' automatically. Alternatively, you may configure 'scalaClasspath' explicitly."
    }
}
