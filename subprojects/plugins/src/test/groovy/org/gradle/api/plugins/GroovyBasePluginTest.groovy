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

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test

import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */

class GroovyBasePluginTest {
    private final Project project = HelperUtil.createRootProject()

    @Before
    void before() {
        project.plugins.apply(GroovyBasePlugin)
    }

    @Test void appliesTheJavaBasePluginToTheProject() {
        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin));
    }

    @Test void addsGroovyConfigurationToTheProject() {
        def configuration = project.configurations.findByName('groovy')
        assertNotNull(configuration)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)
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
        assertThat(task.destinationDir, equalTo(new File(project.docsDir, 'groovydoc')))
        assertThat(task.docTitle, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
        assertThat(task.windowTitle, equalTo(project.extensions.getByType(ReportingExtension).apiDocTitle))
    }

    @Test void defaultsGroovyClasspathToGroovyConfigurationIfTheLatterIsNonEmpty() {
        project.sourceSets.create('custom')
        def configuration = project.configurations.groovy
        project.dependencies {
            groovy "org.codehaus.groovy:groovy-all:2.0.5"
        }

        def compileTask = project.tasks.compileCustomGroovy
        assertSame(configuration, compileTask.groovyClasspath)

        def groovydocTask = project.task('groovydoc', type: Groovydoc)
        assertSame(configuration, groovydocTask.groovyClasspath)
    }

    // see GroovyBasePluginIntegrationTest
    @Test void defaultsGroovyClasspathToInferredGroovyDependencyIfGroovyConfigurationIsEmpty() {
    }
}