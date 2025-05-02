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

import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class ScalaPluginTest extends AbstractProjectBuilderSpec {

    private final ScalaPlugin scalaPlugin = TestUtil.newInstance(ScalaPlugin)

    def "applies the java plugin to the project"() {
        when:
        scalaPlugin.apply(project)

        then:
        project.getPlugins().hasPlugin(JavaPlugin)
    }

    def "adds scala convention to each source set and applies mappings"() {
        when:
        scalaPlugin.apply(project)

        then:
        def sourceSet = project.sourceSets.main
        sourceSet.scala.displayName == "main Scala source"
        sourceSet.scala.srcDirs == toLinkedSet(project.file("src/main/scala"))

        def testSourceSet = project.sourceSets.test
        testSourceSet.scala.displayName == "test Scala source"
        testSourceSet.scala.srcDirs ==  toLinkedSet(project.file("src/test/scala"))
    }

    def "adds compile task for each source set"() {
        when:
        scalaPlugin.apply(project)

        then:
        def task = project.tasks['compileScala']
        SourceSet mainSourceSet = project.sourceSets.main
        task instanceof ScalaCompile
        task.description == 'Compiles the main Scala source.'
        task.classpath.files as List == [mainSourceSet.java.destinationDirectory.get().asFile]
        task.source as List == mainSourceSet.scala  as List
        task dependsOn(JvmConstants.COMPILE_JAVA_TASK_NAME)

        def testTask = project.tasks['compileTestScala']
        def testSourceSet = project.sourceSets.test
        testTask instanceof ScalaCompile
        testTask.description == 'Compiles the test Scala source.'
        testTask.classpath.files as List == [
            mainSourceSet.java.destinationDirectory.get().asFile,
            mainSourceSet.scala.destinationDirectory.get().asFile,
            mainSourceSet.output.resourcesDir,
            testSourceSet.java.destinationDirectory.get().asFile,
        ]
        testTask.source as List == testSourceSet.scala as List
        testTask dependsOn(JvmConstants.COMPILE_TEST_JAVA_TASK_NAME, JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
    }

    def "compile dependency to java compilation can be turned off by changing the compile task classpath"() {
        when:
        scalaPlugin.apply(project)

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

    def "dependencies of java plugin tasks include scala compile tasks"() {
        when:
        scalaPlugin.apply(project)

        then:
        def task = project.tasks[JvmConstants.CLASSES_TASK_NAME]
        task dependsOn('compileScala', JvmConstants.COMPILE_JAVA_TASK_NAME, JvmConstants.PROCESS_RESOURCES_TASK_NAME)

        def testTask = project.tasks[JvmConstants.TEST_CLASSES_TASK_NAME]
        testTask dependsOn('compileTestScala', JvmConstants.COMPILE_TEST_JAVA_TASK_NAME, 'processTestResources')
    }

    def "adds scala doc tasks to the project"() {
        when:
        scalaPlugin.apply(project)

        then:
        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        task instanceof ScalaDoc
        task dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        task.destinationDir == project.java.docsDir.file("scaladoc").get().asFile
        task.source as List == project.sourceSets.main.scala as List // We take sources of main
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        task.title == project.extensions.getByType(ReportingExtension).apiDocTitle
    }

    def "configures scala doc tasks defined by the build script"() {
        when:
        scalaPlugin.apply(project)

        then:
        def task = project.task('otherScaladoc', type: ScalaDoc)
        task dependsOn(JvmConstants.CLASSES_TASK_NAME, JvmConstants.COMPILE_JAVA_TASK_NAME, 'compileScala')
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
    }
}
