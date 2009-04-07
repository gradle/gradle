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
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import org.gradle.util.WrapUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class GroovyPluginTest {
    @Test public void testApply() {
        // todo Make test stronger
        // This is a very weak test. But due to the dynamic nature of Groovy, it does help to find bugs.
        Project project = HelperUtil.createRootProject()
        GroovyPlugin groovyPlugin = new GroovyPlugin()
        groovyPlugin.apply(project, new PluginRegistry(), null)

        assertTrue(project.getAppliedPlugins().contains(JavaPlugin));

        def configuration = project.configurations.get(JavaPlugin.COMPILE)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet(GroovyPlugin.GROOVY)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.configurations.get(GroovyPlugin.GROOVY)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        def task = project.tasks[JavaPlugin.COMPILE]
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.srcDirs as Object[]))
        assertThat(task.groovySourceDirs, hasItems(project.convention.plugins.groovy.groovySrcDirs as Object[]))

        task = project.tasks[JavaPlugin.TEST_COMPILE]
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.testSrcDirs as Object[]))
        assertThat(task.groovySourceDirs, hasItems(project.convention.plugins.groovy.groovyTestSrcDirs as Object[]))

        task = project.tasks[JavaPlugin.JAVADOC]
        assertThat(task, instanceOf(Javadoc.class))
        assertThat(((Javadoc)task).srcDirs, hasItems(project.convention.plugins.java.srcDirs as Object[]))
        assertThat(((Javadoc)task).srcDirs, hasItems(project.convention.plugins.groovy.groovySrcDirs as Object[]))
        assertThat(((Javadoc)task).exclude, hasItem('**/*.groovy'))

        task = project.tasks[GroovyPlugin.GROOVYDOC]
        assertThat(task, instanceOf(Groovydoc.class))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.groovy.groovydocDir))
        assertThat(task.srcDirs, not(hasItems(project.convention.plugins.java.srcDirs as Object[])))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.groovy.groovySrcDirs as Object[]))
    }
}
