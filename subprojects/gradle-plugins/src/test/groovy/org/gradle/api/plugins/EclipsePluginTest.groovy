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


import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.util.HelperUtil
import org.gradle.api.Project
import org.junit.Test
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.tasks.ide.eclipse.ProjectType

public class EclipsePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final EclipsePlugin plugin = new EclipsePlugin()

    @Test
    public void canApplyToProject() {
        plugin.use(project, project.getPlugins())
    }

    @Test
    public void addsTasksWhenJavaPluginApplied() {
        plugin.use(project, project.getPlugins())
        project.usePlugin(JavaPlugin)

        def task = project.tasks[EclipsePlugin.ECLIPSE_TASK_NAME]
        assertThat(task, dependsOn(EclipsePlugin.ECLIPSE_PROJECT_TASK_NAME, EclipsePlugin.ECLIPSE_CP_TASK_NAME))

        task = project.tasks[EclipsePlugin.ECLIPSE_PROJECT_TASK_NAME]
        assertThat(task, dependsOn())
        assertThat(task.projectName, equalTo(project.name))
        assertThat(task.natureNames, equalTo(ProjectType.JAVA.natureNames() as Set))
        assertThat(task.buildCommandNames, equalTo(ProjectType.JAVA.buildCommandNames() as Set))

        task = project.tasks[EclipsePlugin.ECLIPSE_CP_TASK_NAME]
        assertThat(task, dependsOn())

        task = project.tasks[EclipsePlugin.ECLIPSE_CLEAN_TASK_NAME]
        assertThat(task, dependsOn())
    }

    @Test
    public void addsTasksWhenGroovyPluginApplied() {
        plugin.use(project, project.getPlugins())
        project.usePlugin(GroovyPlugin)

        def task = project.tasks[EclipsePlugin.ECLIPSE_PROJECT_TASK_NAME]
        assertThat(task, dependsOn())
        assertThat(task.projectName, equalTo(project.name))
        assertThat(task.natureNames, equalTo(ProjectType.GROOVY.natureNames() as Set))
        assertThat(task.buildCommandNames, equalTo(ProjectType.GROOVY.buildCommandNames() as Set))
    }

    @Test
    public void addsTasksWhenScalaPluginApplied() {
        plugin.use(project, project.getPlugins())
        project.usePlugin(ScalaPlugin)

        def task = project.tasks[EclipsePlugin.ECLIPSE_PROJECT_TASK_NAME]
        assertThat(task, dependsOn())
        assertThat(task.projectName, equalTo(project.name))
        assertThat(task.natureNames, equalTo(ProjectType.SCALA.natureNames() as Set))
        assertThat(task.buildCommandNames, equalTo(ProjectType.SCALA.buildCommandNames() as Set))
    }
    
    @Test
    public void addsTasksWhenWarPluginApplied() {
        plugin.use(project, project.getPlugins())
        project.usePlugin(WarPlugin)

        def task = project.tasks[EclipsePlugin.ECLIPSE_TASK_NAME]
        assertThat(task, dependsOn(EclipsePlugin.ECLIPSE_PROJECT_TASK_NAME, EclipsePlugin.ECLIPSE_CP_TASK_NAME, EclipsePlugin.ECLIPSE_WTP_TASK_NAME))

        task = project.tasks[EclipsePlugin.ECLIPSE_WTP_TASK_NAME]
        assertThat(task, dependsOn())
    }
}

