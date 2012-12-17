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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.api.artifacts.Configuration
import org.gradle.util.HelperUtil

import org.junit.Test

import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class ScalaBasePluginTest {
    private final Project project = HelperUtil.createRootProject()

    @Test void appliesTheJavaPluginToTheProject() {
        project.plugins.apply(ScalaBasePlugin)
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin))
    }

    @Test void addsScalaToolsConfigurationToTheProject() {
        project.plugins.apply(ScalaBasePlugin)
        def configuration = project.configurations.getByName(ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test void defaultsScalaClasspathToScalaToolsConfigurationIfTheLatterIsNonEmpty() {
        project.plugins.apply(ScalaBasePlugin)
        project.sourceSets.add('custom')
        def configuration = project.configurations.scalaTools
        project.dependencies {
            scalaTools "org.scala-lang:scala-compiler:2.10"
        }

        def compileTask = project.tasks.compileCustomScala
        assertSame(configuration, compileTask.scalaClasspath)

        def consoleTask = project.tasks.scalaCustomConsole
        assertSame(configuration, consoleTask.classpath)

        def scaladocTask = project.task("scaladoc", type: ScalaDoc)
        assertSame(configuration, scaladocTask.scalaClasspath)
    }

    // see ScalaBasePluginIntegrationTest
    @Test void defaultsScalaClasspathToInferredScalaCompilerDependencyIfScalaToolsConfigurationIsEmpty() {
    }

    @Test void addsZincConfigurationToTheProject() {
        project.plugins.apply(ScalaBasePlugin)
        def configuration = project.configurations.getByName(ScalaBasePlugin.ZINC_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test void preconfiguresZincClasspathForCompileTasksThatUseZinc() {
        project.plugins.apply(ScalaBasePlugin)
        project.sourceSets.add('custom')
        def task = project.tasks.compileCustomScala
        task.scalaCompileOptions.useAnt = false
        assert task.zincClasspath instanceof Configuration
        assert task.zincClasspath.dependencies.find { it.name.contains('zinc') }
    }

    @Test void doesNotPreconfigureZincClasspathForCompileTasksThatUseAnt() {
        project.plugins.apply(ScalaBasePlugin)
        project.sourceSets.add('custom')
        def task = project.tasks.compileCustomScala
        task.scalaCompileOptions.useAnt = true
        assert task.zincClasspath instanceof Configuration
        assert task.zincClasspath.empty
    }

    @Test void addsScalaConventionToNewSourceSet() {
        project.plugins.apply(ScalaBasePlugin)

        def sourceSet = project.sourceSets.add('custom')
        assertThat(sourceSet.scala.displayName, equalTo("custom Scala source"))
        assertThat(sourceSet.scala.srcDirs, equalTo(toLinkedSet(project.file("src/custom/scala"))))
    }

    @Test void addsCompileTaskForNewSourceSet() {
        project.plugins.apply(ScalaBasePlugin)

        project.sourceSets.add('custom')
        def task = project.tasks['compileCustomScala']
        assertThat(task, instanceOf(ScalaCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Scala source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.custom.compileClasspath))
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task.source as List, equalTo(project.sourceSets.custom.scala as List))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test void preconfiguresIncrementalCompileOptions() {
        project.plugins.apply(ScalaBasePlugin)

        project.sourceSets.add('custom')
        project.tasks.add('customJar', Jar)
        ScalaCompile task = project.tasks['compileCustomScala']
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.scalaCompileOptions.incrementalOptions.analysisFile, equalTo(new File("$project.buildDir/tmp/scala/compilerAnalysis/compileCustomScala.analysis")))
        assertThat(task.scalaCompileOptions.incrementalOptions.publishedCode, equalTo(project.tasks['customJar'].archivePath))
    }

    @Test void incrementalCompileOptionsCanBeOverridden() {
        project.plugins.apply(ScalaBasePlugin)

        project.sourceSets.add('custom')
        project.tasks.add('customJar', Jar)
        ScalaCompile task = project.tasks['compileCustomScala']
        task.scalaCompileOptions.incrementalOptions.analysisFile = new File("/my/file")
        task.scalaCompileOptions.incrementalOptions.publishedCode = new File("/my/published/code.jar")
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.scalaCompileOptions.incrementalOptions.analysisFile, equalTo(new File("/my/file")))
        assertThat(task.scalaCompileOptions.incrementalOptions.publishedCode, equalTo(new File("/my/published/code.jar")))
    }
    
    @Test void dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        project.plugins.apply(ScalaBasePlugin)

        project.sourceSets.add('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomScala')))
    }

    @Test void configuresCompileTasksDefinedByTheBuildScript() {
        project.plugins.apply(ScalaBasePlugin)

        def task = project.task('otherCompile', type: ScalaCompile)
        assertThat(task.source, isEmpty())
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task, dependsOn())
    }

    @Test void configuresScalaDocTasksDefinedByTheBuildScript() {
        project.plugins.apply(ScalaBasePlugin)

        def task = project.task('otherScaladoc', type: ScalaDoc)
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/scaladoc")))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task.scalaClasspath, equalTo(project.configurations[ScalaBasePlugin.SCALA_TOOLS_CONFIGURATION_NAME]))
        assertThat(task, dependsOn())
    }
}