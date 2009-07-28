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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.Clean
import org.gradle.api.tasks.Upload
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.WrapUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class BasePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final BasePlugin plugin = new BasePlugin()

    @Test public void addsConventionObject() {
        plugin.use(project, project.getPlugins())

        assertThat(project.convention.plugins.base, instanceOf(BasePluginConvention))
    }

    @Test public void createsTasksAndAppliesMappings() {
        plugin.use(project, project.getPlugins())

        def task = project.tasks[BasePlugin.CLEAN_TASK_NAME]
        assertThat(task, instanceOf(Clean))
        assertDependsOn(task) 
        assertThat(task.dir, equalTo(project.buildDir))
    }

    @Test public void addsImplictTasksForConfiguration() {
        plugin.use(project, project.getPlugins())

        project.tasks.add('producer')
        project.configurations.add('conf').addArtifact([getTaskDependency: {-> new DefaultTaskDependency().add('producer') }] as PublishArtifact)

        def task = project.tasks['buildConf']
        assertThat(task, instanceOf(DefaultTask))
        assertDependsOn(task, 'producer')

        task = project.tasks['uploadConf']
        assertThat(task, instanceOf(Upload))
        assertDependsOn(task, 'producer')
        assertThat(task.configuration, sameInstance(project.configurations.conf))
    }
    
    private void assertDependsOn(Task task, String... names) {
        assertThat(task.taskDependencies.getDependencies(task)*.name as Set, equalTo(toSet(names)))
    }
}
