/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
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
class GroovyPluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final GroovyPlugin groovyPlugin = new GroovyPlugin()

    @Test public void appliesTheJavaPluginToTheProject() {
        groovyPlugin.use(project, project.getPlugins())

        assertTrue(project.getPlugins().hasPlugin(JavaPlugin));
    }

    @Test public void addsGroovyConfigurationToTheProject() {
        groovyPlugin.use(project, project.getPlugins())

        def configuration = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(GroovyPlugin.GROOVY_CONFIGURATION_NAME)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.getByName(GroovyPlugin.GROOVY_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)
    }

    @Test public void addsGroovyConventionToEachSourceSetAndAppliesMappings() {
        groovyPlugin.use(project, project.getPlugins())

        def sourceSet = project.source.main
        assertThat(sourceSet.groovy.displayName, equalTo("main Groovy source"))
        assertThat(sourceSet.groovy.srcDirs, equalTo(toLinkedSet(project.file("src/main/groovy"))))

        sourceSet = project.source.test
        assertThat(sourceSet.groovy.displayName, equalTo("test Groovy source"))
        assertThat(sourceSet.groovy.srcDirs, equalTo(toLinkedSet(project.file("src/test/groovy"))))

        sourceSet = project.source.add('custom')
        assertThat(sourceSet.groovy.displayName, equalTo("custom Groovy source"))
        assertThat(sourceSet.groovy.srcDirs, equalTo(toLinkedSet(project.file("src/custom/groovy"))))
    }

    @Test public void replacesCompileTaskForEachSourceSet() {
        groovyPlugin.use(project, project.getPlugins())

        def task = project.tasks[JavaPlugin.COMPILE_TASK_NAME]
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.srcDirs, equalTo(project.source.main.java.srcDirs as List))
        assertThat(task.groovySourceDirs, equalTo(project.source.main.groovy.srcDirs as List))
        assertThat(task, dependsOn())

        task = project.tasks[JavaPlugin.COMPILE_TEST_TASK_NAME]
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.srcDirs, equalTo(project.source.test.java.srcDirs as List))
        assertThat(task.groovySourceDirs, equalTo(project.source.test.groovy.srcDirs as List))
        assertThat(task, dependsOn(JavaPlugin.COMPILE_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME))

        project.source.add('custom')
        task = project.tasks['compileCustom']
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.srcDirs, equalTo(project.source.custom.java.srcDirs as List))
        assertThat(task.groovySourceDirs, equalTo(project.source.custom.groovy.srcDirs as List))
    }

    @Test public void addsStandarcTasksToTheProject() {
        groovyPlugin.use(project, project.getPlugins())

        def task = project.tasks[JavaPlugin.JAVADOC_TASK_NAME]
        assertThat(task, instanceOf(Javadoc.class))
        assertThat(task.srcDirs, hasItems(project.source.main.java.srcDirs as Object[]))
        assertThat(task.srcDirs, hasItems(project.source.main.groovy.srcDirs as Object[]))
        assertThat(task.exclude, hasItem('**/*.groovy'))

        task = project.tasks[GroovyPlugin.GROOVYDOC_TASK_NAME]
        assertThat(task, instanceOf(Groovydoc.class))
        assertThat(task.destinationDir, equalTo(new File(project.docsDir, 'groovydoc')))
        assertThat(task.srcDirs, not(hasItems(project.source.main.java.srcDirs as Object[])))
        assertThat(task.srcDirs, hasItems(project.source.main.groovy.srcDirs as Object[]))
    }

    @Test public void configuresAdditionalTasksDefinedByTheBuildScript() {
        groovyPlugin.use(project, project.getPlugins())
        
        def task = project.createTask('otherCompile', type: GroovyCompile)
        assertThat(task.classpath, sameInstance(project.source.main.compileClasspath))
        assertThat(task.groovySourceDirs, equalTo(project.source.main.groovy.srcDirs as List))

        task = project.createTask('otherJavadoc', type: Javadoc)
        assertThat(task.srcDirs, hasItems(project.source.main.java.srcDirs as Object[]))
        assertThat(task.srcDirs, hasItems(project.source.main.groovy.srcDirs as Object[]))
        assertThat(task.exclude, hasItem('**/*.groovy'))

        task = project.createTask('otherGroovydoc', type: Groovydoc)
        assertThat(task.destinationDir, equalTo(new File(project.docsDir, 'groovydoc')))
        assertThat(task.srcDirs, not(hasItems(project.source.main.java.srcDirs as Object[])))
        assertThat(task.srcDirs, hasItems(project.source.main.groovy.srcDirs as Object[]))
    }
}
