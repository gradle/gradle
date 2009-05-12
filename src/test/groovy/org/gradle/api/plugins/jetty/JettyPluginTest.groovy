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
package org.gradle.api.plugins.jetty

import org.gradle.api.Project
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.plugins.jetty.JettyPlugin
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.plugins.WarPlugin
import org.gradle.util.WrapUtil
import org.gradle.api.plugins.JavaPlugin

public class JettyPluginTest {
    private final Project project = HelperUtil.createRootProject()

    @Test
    public void appliesWarPluginAndAddsConventionToProject() {
        new JettyPlugin().apply(project, new PluginRegistry(), [:])

        assertTrue(project.appliedPlugins.contains(WarPlugin))

        assertThat(project.convention.plugins.jetty, instanceOf(JettyPluginConvention))
    }
    
    @Test
    public void addsTasksToProject() {
        new JettyPlugin().apply(project, new PluginRegistry(), [:])

        def task = project.tasks[JettyPlugin.JETTY_RUN]
        assertThat(task, instanceOf(JettyRun))
        assertThat(task.dependsOn, equalTo(WrapUtil.toSet(JavaPlugin.COMPILE_TESTS_TASK_NAME)))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks[JettyPlugin.JETTY_RUN_EXPLODED_WAR]
        assertThat(task, instanceOf(JettyRunWarExploded))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks[JettyPlugin.JETTY_RUN_WAR]
        assertThat(task, instanceOf(JettyRunWar))
        assertThat(task.dependsOn, equalTo(WrapUtil.toSet(WarPlugin.WAR_TASK_NAME)))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks[JettyPlugin.JETTY_STOP]
        assertThat(task, instanceOf(JettyStop))
        assertThat(task.stopPort, equalTo(project.stopPort))
    }

    @Test
    public void addsMappingToNewJettyTasks() {
        new JettyPlugin().apply(project, new PluginRegistry(), [:])

        def task = project.tasks.add('customRun', JettyRun)
        assertThat(task.dependsOn, equalTo(WrapUtil.toSet(JavaPlugin.COMPILE_TESTS_TASK_NAME)))
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks.add('customRunExploded', JettyRunWarExploded)
        assertThat(task.httpPort, equalTo(project.httpPort))

        task = project.tasks.add('customWar', JettyRunWar)
        assertThat(task.dependsOn, equalTo(WrapUtil.toSet(WarPlugin.WAR_TASK_NAME)))
        assertThat(task.httpPort, equalTo(project.httpPort))
    }
}
