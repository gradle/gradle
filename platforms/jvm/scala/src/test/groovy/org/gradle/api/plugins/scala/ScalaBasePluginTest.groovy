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
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.CoreMatchers.hasItem

class ScalaBasePluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(ScalaBasePlugin)
    }

    def appliesTheJavaPluginToTheProject() {
        expect:
        project.getPlugins().hasPlugin(JavaBasePlugin)
    }

    def addsZincConfigurationToTheProject() {
        when:
        def configuration = project.configurations.getByName(ScalaBasePlugin.ZINC_CONFIGURATION_NAME)

        then:
        configuration.extendsFrom.empty
        !configuration.visible
        configuration.transitive
    }

    def preconfiguresZincClasspathForCompileTasksThatUseZinc() {
        when:
        project.sourceSets.create('custom')
        def task = project.tasks.compileCustomScala

        then:
        task.zincClasspath instanceof Configuration
        task.zincClasspath.incoming.dependencies.find { it.name.contains('zinc') }
    }

    def addsScalaConventionToNewSourceSet() {
        when:
        def sourceSet = project.sourceSets.create('custom')

        then:
        sourceSet.scala.displayName == "custom Scala source"
        sourceSet.scala.srcDirs == [project.file("src/custom/scala")] as Set
    }

    def addsCompileTaskForNewSourceSet() {
        when:
        project.sourceSets.create('custom')
        SourceSet customSourceSet = project.sourceSets.custom
        def task = project.tasks['compileCustomScala']

        then:
        task instanceof ScalaCompile
        task.description == 'Compiles the custom Scala source.'
        task.classpath.files as List == [
            customSourceSet.java.destinationDirectory.get().asFile
        ]
        task.source as List == customSourceSet.scala as List
        task dependsOn('compileCustomJava')
    }

    def preconfiguresIncrementalCompileOptions() {
        when:
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        def task = project.tasks['compileCustomScala']
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        then:
        task.scalaCompileOptions.incrementalOptions.analysisFile.get().asFile == new File("$project.buildDir/tmp/scala/compilerAnalysis/compileCustomScala.analysis")
        task.scalaCompileOptions.incrementalOptions.classfileBackupDir.get().asFile == new File("$project.buildDir/tmp/scala/classfileBackup/compileCustomScala.bak")
        task.scalaCompileOptions.incrementalOptions.publishedCode.get().asFile == project.tasks['customJar'].archiveFile.get().asFile
        task.analysisMappingFile.get().asFile == new File("$project.buildDir/tmp/scala/compilerAnalysis/compileCustomScala.mapping")
    }

    def incrementalCompileOptionsCanBeOverridden() {
        when:
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        def task = project.tasks['compileCustomScala']
        task.scalaCompileOptions.incrementalOptions.analysisFile.set(project.file("my/file"))
        task.scalaCompileOptions.incrementalOptions.classfileBackupDir.set(project.file("my/classes.bak"))
        task.scalaCompileOptions.incrementalOptions.publishedCode.set(project.file("my/published/code.jar"))
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        then:
        task.scalaCompileOptions.incrementalOptions.analysisFile.get().asFile == project.file("my/file")
        task.scalaCompileOptions.incrementalOptions.classfileBackupDir.get().asFile == project.file("my/classes.bak")
        task.scalaCompileOptions.incrementalOptions.publishedCode.get().asFile == project.file("my/published/code.jar")
    }

    def dependenciesOfJavaPluginTasksIncludeScalaCompileTasks() {
        when:
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']

        then:
        task dependsOn(hasItem('compileCustomScala'))
    }

    def configuresCompileTasksDefinedByTheBuildScript() {
        when:
        def task = project.task('otherCompile', type: ScalaCompile)

        then:
        task.source.isEmpty()
        task dependsOn()
    }

    def configuresScalaDocTasksDefinedByTheBuildScript() {
        when:
        def task = project.task('otherScaladoc', type: ScalaDoc)

        then:
        task.destinationDir == project.file("$project.docsDir/scaladoc")
        task.title == project.extensions.getByType(ReportingExtension).apiDocTitle
        task dependsOn()
    }
}
