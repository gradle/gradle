/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.gosu

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.gosu.GosuCompile
import org.gradle.api.tasks.gosu.GosuDoc
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class GosuBasePluginTest {
    private final DefaultProject project = TestUtil.createRootProject()

    @Before
    void before() {
        project.pluginManager.apply(GosuBasePlugin)
    }

    @Test
    void appliesTheJavaPluginToTheProject() {
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin))
    }

    @Test
    void addsGosuConventionToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertThat(sourceSet.gosu.displayName, equalTo("custom Gosu source"))
        assertThat(sourceSet.gosu.srcDirs, equalTo(toLinkedSet(project.file("src/custom/gosu"))))
    }

    @Test
    void addsCompileTaskForNewSourceSet() {
        project.sourceSets.create('custom')
        def task = project.tasks['compileCustomGosu']
        assertThat(task, instanceOf(GosuCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Gosu source.'))
        assertThat(task.classpath, equalTo(project.sourceSets.custom.compileClasspath))
        assertThat(task.source as List, equalTo(project.sourceSets.custom.gosu as List))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test
    void dependenciesOfJavaPluginTasksIncludeGosuCompileTasks() {
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomGosu')))
    }

    @Test
    void configuresCompileTasksDefinedByTheBuildScript() {
        def task = project.task('otherCompile', type: GosuCompile)
        assertThat(task.source, isEmpty())
        assertThat(task, dependsOn())
    }

    @Test
    void configuresGosuDocTasksDefinedByTheBuildScript() {
        def task = project.task('otherGosudoc', type: GosuDoc)
        assertThat(task.destinationDir, equalTo(project.file("$project.docsDir/gosudoc")))
        assertThat(task.title, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task, dependsOn())
    }
}
