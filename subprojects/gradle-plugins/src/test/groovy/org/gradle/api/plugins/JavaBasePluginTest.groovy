/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.*
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */

class JavaBasePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final JavaBasePlugin javaBasePlugin = new JavaBasePlugin()

    @Test public void appliesBasePluginsAndAddsConventionObject() {
        javaBasePlugin.apply(project)

        assertTrue(project.getPlugins().hasPlugin(ReportingBasePlugin))
        assertTrue(project.getPlugins().hasPlugin(BasePlugin))

        assertThat(project.convention.plugins.java, instanceOf(JavaPluginConvention))
    }

    @Test public void createsTasksAndAppliesMappingsForNewSourceSet() {
        javaBasePlugin.apply(project)

        project.sourceSets.add('custom')
        def set = project.sourceSets.custom
        assertThat(set.java.srcDirs, equalTo(toLinkedSet(project.file('src/custom/java'))))
        assertThat(set.resources.srcDirs, equalTo(toLinkedSet(project.file('src/custom/resources'))))
        assertThat(set.classesDir, equalTo(new File(project.buildDir, 'classes/custom')))
        assertThat(set.classes, builtBy('customClasses'))

        def task = project.tasks['processCustomResources']
        assertThat(task.description, equalTo('Processes the custom resources.'))
        assertThat(task, instanceOf(Copy))
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.resources))

        task = project.tasks['compileCustomJava']
        assertThat(task.description, equalTo('Compiles the custom Java source.'))
        assertThat(task, instanceOf(Compile))
        assertThat(task, dependsOn())
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.java))
        assertThat(task.classpath, sameInstance(project.sourceSets.custom.compileClasspath))
        assertThat(task.destinationDir, equalTo(project.sourceSets.custom.classesDir))

        task = project.tasks['customClasses']
        assertThat(task.description, equalTo('Assembles the custom classes.'))
        assertThat(task, instanceOf(DefaultTask))
        assertThat(task, dependsOn('processCustomResources', 'compileCustomJava'))
    }

    @Test public void appliesMappingsToTasksDefinedByBuildScript() {
        javaBasePlugin.apply(project)

        def task = project.createTask('customCompile', type: Compile)
        assertThat(task.sourceCompatibility, equalTo(project.sourceCompatibility.toString()))

        task = project.createTask('customTest', type: org.gradle.api.tasks.testing.Test)
        assertThat(task.workingDir, equalTo(project.projectDir))

        task = project.createTask('customJavadoc', type: Javadoc)
        assertThat(task.destinationDir, equalTo((project.file("$project.docsDir/javadoc"))))
        assertThat(task.title, equalTo(project.apiDocTitle))
    }

    @Test public void appliesMappingsToCustomJarTasks() {
        javaBasePlugin.apply(project)

        def task = project.createTask('customJar', type: Jar)
        assertThat(task, dependsOn())
        assertThat(task.destinationDir, equalTo(project.libsDir))
    }

    @Test public void createsLifecycleBuildTasks() {
        javaBasePlugin.apply(project)
        
        def build = project.tasks[JavaBasePlugin.BUILD_TASK_NAME]
        assertThat(build, dependsOn(JavaBasePlugin.CHECK_TASK_NAME, BasePlugin.ASSEMBLE_TASK_NAME))

        def buildDependent = project.tasks[JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME]
        assertThat(buildDependent, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))

        def buildNeeded = project.tasks[JavaBasePlugin.BUILD_NEEDED_TASK_NAME]
        assertThat(buildNeeded, dependsOn(JavaBasePlugin.BUILD_TASK_NAME))
    }
}