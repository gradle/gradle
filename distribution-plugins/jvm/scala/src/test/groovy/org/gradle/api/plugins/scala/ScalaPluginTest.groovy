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
import org.gradle.api.file.FileCollectionMatchers
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class ScalaPluginTest {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final Project project = TestUtil.create(temporaryFolder).rootProject()
    private final ScalaPlugin scalaPlugin = new ScalaPlugin()

    @Test void appliesTheJavaPluginToTheProject() {
        scalaPlugin.apply(project)
        assertTrue(project.getPlugins().hasPlugin(JavaPlugin))
    }

    @Test void addsScalaConventionToEachSourceSetAndAppliesMappings() {
        scalaPlugin.apply(project)

        def sourceSet = project.sourceSets.main
        assertThat(sourceSet.scala.displayName, equalTo("main Scala source"))
        assertThat(sourceSet.scala.srcDirs, equalTo(toLinkedSet(project.file("src/main/scala"))))

        sourceSet = project.sourceSets.test
        assertThat(sourceSet.scala.displayName, equalTo("test Scala source"))
        assertThat(sourceSet.scala.srcDirs, equalTo(toLinkedSet(project.file("src/test/scala"))))
    }

    @Test void addsCompileTaskForEachSourceSet() {
        scalaPlugin.apply(project)

        def task = project.tasks['compileScala']
        SourceSet mainSourceSet = project.sourceSets.main
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.description, equalTo('Compiles the main Scala source.'))
        assertThat(task.classpath.files as List, equalTo([
            mainSourceSet.java.destinationDirectory.get().asFile
        ]))
        assertThat(task.source as List, equalTo(mainSourceSet.scala  as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME))

        task = project.tasks['compileTestScala']
        def testSourceSet = project.sourceSets.test
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.description, equalTo('Compiles the test Scala source.'))
        assertThat(task.classpath.files as List, equalTo([
            mainSourceSet.java.destinationDirectory.get().asFile,
            mainSourceSet.scala.destinationDirectory.get().asFile,
            mainSourceSet.output.resourcesDir,
            testSourceSet.java.destinationDirectory.get().asFile,
        ]))
        assertThat(task.source as List, equalTo(testSourceSet.scala as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaPlugin.CLASSES_TASK_NAME))
    }

    @Test void "compile dependency to java compilation can be turned off by changing the compile task classpath"() {
        scalaPlugin.apply(project)

        def task = project.tasks['compileScala']
        task.classpath = project.sourceSets.main.compileClasspath

        SourceSet mainSourceSet = project.sourceSets.main
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.classpath.files as List, equalTo([]))
        assertThat(task.source as List, equalTo(mainSourceSet.scala  as List))
        assertThat(task, not(dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME)))

        task = project.tasks['compileTestScala']
        task.classpath = project.sourceSets.test.compileClasspath

        def testSourceSet = project.sourceSets.test
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.classpath.files as List, equalTo([
            mainSourceSet.java.destinationDirectory.get().asFile,
            mainSourceSet.scala.destinationDirectory.get().asFile,
            mainSourceSet.output.resourcesDir
        ]))
        assertThat(task.source as List, equalTo(testSourceSet.scala as List))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task, not(dependsOn(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME)))
    }

    @Test void dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        scalaPlugin.apply(project)

        def task = project.tasks[JavaPlugin.CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileScala')))

        task = project.tasks[JavaPlugin.TEST_CLASSES_TASK_NAME]
        assertThat(task, dependsOn(hasItem('compileTestScala')))
    }

    @Test void addsScalaDocTasksToTheProject() {
        scalaPlugin.apply(project)

        def task = project.tasks[ScalaPlugin.SCALA_DOC_TASK_NAME]
        assertThat(task, instanceOf(ScalaDoc.class))
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/scaladoc")))
        assertThat(task.source as List, equalTo(project.sourceSets.main.scala as List))
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
    }

    @Test void configuresScalaDocTasksDefinedByTheBuildScript() {
        scalaPlugin.apply(project)

        def task = project.task('otherScaladoc', type: ScalaDoc)
        assertThat(task, dependsOn(JavaPlugin.CLASSES_TASK_NAME))
        assertThat(task.classpath, FileCollectionMatchers.sameCollection(project.layout.files(project.sourceSets.main.output, project.sourceSets.main.compileClasspath)))
    }
}
