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
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.HelperUtil
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Test

/**
 * @author Hans Dockter
 */
class GroovyPluginTest {
    @Test public void testApply() {
        // todo Make test stronger
        // This is a very weak test. But due to the dynamic nature of Groovy, it does help to find bugs.
        Project project = HelperUtil.createRootProject(new File('path', 'root'))
        GroovyPlugin groovyPlugin = new GroovyPlugin()
        groovyPlugin.apply(project, new PluginRegistry())

        def configuration = project.dependencies.configurations[JavaPlugin.COMPILE]
        assertThat(configuration.extendsFrom, equalTo(toSet(GroovyPlugin.GROOVY)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.dependencies.configurations[GroovyPlugin.GROOVY]
        assertThat(configuration.extendsFrom, equalTo(toSet()))
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
        assertThat(task.srcDirs, hasItems(project.convention.plugins.java.srcDirs as Object[]))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.groovy.groovySrcDirs as Object[]))
        assertThat(task.excludes, hasItem('**/*.groovy'))

        task = project.tasks[GroovyPlugin.GROOVYDOC]
        assertThat(task, instanceOf(Groovydoc.class))
        assertThat(task.destinationDir, equalTo(project.convention.plugins.groovy.groovydocDir))
        assertThat(task.srcDirs, not(hasItems(project.convention.plugins.java.srcDirs as Object[])))
        assertThat(task.srcDirs, hasItems(project.convention.plugins.groovy.groovySrcDirs as Object[]))
    }
}
