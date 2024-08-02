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

package org.gradle.api.plugins

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.hamcrest.CoreMatchers.*
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class GroovyBasePluginTest {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    private ProjectInternal project

    @Before
    void before() {
        project = TestUtil.create(temporaryFolder).rootProject()
        project.pluginManager.apply(GroovyBasePlugin)
    }

    @Test void appliesTheJavaBasePluginToTheProject() {
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin));
    }

    @Test void appliesMappingsToNewSourceSet() {
        def sourceSet = project.sourceSets.create('custom')
        assertThat(sourceSet.groovy.displayName, equalTo("custom Groovy source"))
        assertThat(sourceSet.groovy.srcDirs, equalTo(toLinkedSet(project.file("src/custom/groovy"))))
    }

    @Test void addsCompileTaskToNewSourceSet() {
        project.sourceSets.create('custom')

        def task = project.tasks['compileCustomGroovy']
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Groovy source.'))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test void dependenciesOfJavaPluginTasksIncludeGroovyCompileTasks() {
        project.sourceSets.create('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomGroovy')))
    }

    @Test void configuresAdditionalTasksDefinedByTheBuildScript() {
        def task = project.task('otherGroovydoc', type: Groovydoc)
        assertThat(task.destinationDir.asFile.get(), equalTo(new File(project.docsDir, 'groovydoc')))
        assertThat(task.docTitle.get(), equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task.windowTitle.get(), equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
    }
}
