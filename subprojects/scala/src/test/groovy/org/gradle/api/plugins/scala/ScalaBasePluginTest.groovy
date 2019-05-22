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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.*

class ScalaBasePluginTest {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    private final Project project = TestUtil.create(temporaryFolder).rootProject()

    @Before
    void before() {
        project.pluginManager.apply(ScalaBasePlugin)
    }

    @Test
    void appliesTheJavaPluginToTheProject() {
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin))
    }

    @Test
    void addsZincConfigurationToTheProject() {
        def configuration = project.configurations.getByName(ScalaBasePlugin.ZINC_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test
    void preconfiguresZincClasspathForCompileTasksThatUseZinc() {
        project.sourceSets.create('custom')
        def task = project.tasks.compileCustomScala
        assert task.zincClasspath instanceof Configuration
        assert task.zincClasspath.incoming.dependencies.find { it.name.contains('zinc') }
    }

    @Test
    void addsScalaConventionToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertThat(sourceSet.scala.displayName, equalTo("custom Scala source"))
        assertThat(sourceSet.scala.srcDirs, equalTo(toLinkedSet(project.file("src/custom/scala"))))
    }

    @Test
    void addsCompileTaskForNewSourceSet() {
        project.sourceSets.create('custom')
        SourceSet customSourceSet = project.sourceSets.custom
        def task = project.tasks['compileCustomScala']
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Scala source.'))
        assertThat(task.classpath.files as List, equalTo([
            customSourceSet.java.outputDir
        ]))
        assertThat(task.source as List, equalTo(customSourceSet.scala as List))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test
    void preconfiguresIncrementalCompileOptions() {
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        ScalaCompile task = project.tasks['compileCustomScala']
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.scalaCompileOptions.incrementalOptions.analysisFile.get().asFile, equalTo(new File("$project.buildDir/tmp/scala/compilerAnalysis/compileCustomScala.analysis")))
        assertThat(task.scalaCompileOptions.incrementalOptions.publishedCode.get().asFile, equalTo(project.tasks['customJar'].archivePath))
        assertThat(task.analysisMappingFile.get().asFile, equalTo(new File("$project.buildDir/tmp/scala/compilerAnalysis/compileCustomScala.mapping")))
    }

    @Test
    void incrementalCompileOptionsCanBeOverridden() {
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        ScalaCompile task = project.tasks['compileCustomScala']
        task.scalaCompileOptions.incrementalOptions.analysisFile.set(project.file("my/file"))
        task.scalaCompileOptions.incrementalOptions.publishedCode.set(project.file("my/published/code.jar"))
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.scalaCompileOptions.incrementalOptions.analysisFile.get().asFile, equalTo(project.file("my/file")))
        assertThat(task.scalaCompileOptions.incrementalOptions.publishedCode.get().asFile, equalTo(project.file("my/published/code.jar")))
    }

    @Test
    void dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomScala')))
    }

    @Test
    void configuresCompileTasksDefinedByTheBuildScript() {
        def task = project.task('otherCompile', type: ScalaCompile)
        assertThat(task.source, isEmpty())
        assertThat(task, dependsOn())
    }

    @Test
    void configuresScalaDocTasksDefinedByTheBuildScript() {
        def task = project.task('otherScaladoc', type: ScalaDoc)
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/scaladoc")))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task, dependsOn())
    }
}
