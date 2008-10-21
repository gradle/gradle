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
import org.gradle.util.HelperUtil
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Test
import org.gradle.api.dependencies.Dependency

/**
 * @author Hans Dockter
 */
class JavaPluginTest {
    @Test public void testApply() {
        // todo Make test stronger
        // This is a very weak test. But due to the dynamic nature of Groovy, it does help to find bugs.
        Project project = HelperUtil.createRootProject(new File('path', 'root'))
        JavaPlugin javaPlugin = new JavaPlugin()
        javaPlugin.apply(project, null)

        def configuration = project.dependencies.configurations[JavaPlugin.COMPILE]
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.dependencies.configurations[JavaPlugin.RUNTIME]
        assertThat(configuration.extendsFrom, equalTo(toSet(JavaPlugin.COMPILE)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.dependencies.configurations[JavaPlugin.TEST_COMPILE]
        assertThat(configuration.extendsFrom, equalTo(toSet(JavaPlugin.COMPILE)))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)

        configuration = project.dependencies.configurations[JavaPlugin.TEST_RUNTIME]
        assertThat(configuration.extendsFrom, equalTo(toSet(JavaPlugin.TEST_COMPILE, JavaPlugin.RUNTIME)))
        assertFalse(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.dependencies.configurations[JavaPlugin.LIBS]
        assertThat(configuration.extendsFrom, equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.dependencies.configurations[Dependency.DEFAULT_CONFIGURATION]
        assertThat(configuration.extendsFrom, equalTo(toSet(Dependency.MASTER_CONFIGURATION, JavaPlugin.RUNTIME)))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.dependencies.configurations[Dependency.MASTER_CONFIGURATION]
        assertThat(configuration.extendsFrom, equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)

        configuration = project.dependencies.configurations[JavaPlugin.DISTS]
        assertThat(configuration.extendsFrom, equalTo(toSet()))
        assertTrue(configuration.visible)
        assertTrue(configuration.transitive)
    }
}
