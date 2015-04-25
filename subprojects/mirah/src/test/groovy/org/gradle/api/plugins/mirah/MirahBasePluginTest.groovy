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
package org.gradle.api.plugins.mirah

import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.mirah.MirahCompile
import org.gradle.api.tasks.mirah.MirahDoc
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class MirahBasePluginTest {
    private final DefaultProject project = TestUtil.createRootProject()

    @Before
    void before() {
        project.pluginManager.apply(MirahBasePlugin)
    }

    @Test
    void appliesTheJavaPluginToTheProject() {
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin))
    }

    @Test
    void addsZincConfigurationToTheProject() {
        def configuration = project.configurations.getByName(MirahBasePlugin.ZINC_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
    }

    @Test
    void addsMirahConventionToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertThat(sourceSet.mirah.displayName, equalTo("custom Mirah source"))
        assertThat(sourceSet.mirah.srcDirs, equalTo(toLinkedSet(project.file("src/custom/mirah"))))
    }

    @Test
    void addsCompileTaskForNewSourceSet() {
        project.sourceSets.create('custom')
        def task = project.tasks['compileCustomMirah']
        assertThat(task, instanceOf(MirahCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Mirah source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.custom.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.custom.mirah as List))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test
    void preconfiguresIncrementalCompileOptions() {
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        MirahCompile task = project.tasks['compileCustomMirah']
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.mirahCompileOptions.incrementalOptions.analysisFile, equalTo(new File("$project.buildDir/tmp/mirah/compilerAnalysis/compileCustomMirah.analysis")))
        assertThat(task.mirahCompileOptions.incrementalOptions.publishedCode, equalTo(project.tasks['customJar'].archivePath))
    }

    @Test
    void incrementalCompileOptionsCanBeOverridden() {
        project.sourceSets.create('custom')
        project.tasks.create('customJar', Jar)
        MirahCompile task = project.tasks['compileCustomMirah']
        task.mirahCompileOptions.incrementalOptions.analysisFile = new File("/my/file")
        task.mirahCompileOptions.incrementalOptions.publishedCode = new File("/my/published/code.jar")
        project.gradle.buildListenerBroadcaster.projectsEvaluated(project.gradle)

        assertThat(task.mirahCompileOptions.incrementalOptions.analysisFile, equalTo(new File("/my/file")))
        assertThat(task.mirahCompileOptions.incrementalOptions.publishedCode, equalTo(new File("/my/published/code.jar")))
    }

    @Test
    void dependenciesOfJavaPluginTasksIncludeMirahCompileTasks() {
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomMirah')))
    }

    @Test
    void configuresCompileTasksDefinedByTheBuildScript() {
        def task = project.task('otherCompile', type: MirahCompile)
        assertThat(task.source, isEmpty())
        assertThat(task, dependsOn())
    }

    @Test
    void configuresMirahDocTasksDefinedByTheBuildScript() {
        def task = project.task('otherMirahdoc', type: MirahDoc)
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/mirahdoc")))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task, dependsOn())
    }
}