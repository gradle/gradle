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
package org.gradle.plugins.ide.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class IdePluginTest extends AbstractProjectBuilderSpec {

    def addsLifecycleTasks() {
        when:
        new TestIdePlugin().apply(project)

        then:
        Task ideTask = project.tasks['testIde']
        ideTask instanceof DefaultTask
        ideTask.group == 'IDE'

        Task cleanTask = project.tasks['cleanTestIde']
        cleanTask instanceof DefaultTask
        cleanTask.group == 'IDE'
    }

    def addsWorkerTask() {
        when:
        new TestIdePlugin().apply(project)

        then:
        Task worker = project.tasks['generateXml']
        Task ideTask = project.tasks['testIde']
        ideTask.taskDependencies.getDependencies(ideTask) == [worker] as Set

        Task cleaner = project.tasks['cleanGenerateXml']
        cleaner instanceof Delete
    }
}

class TestIdePlugin extends IdePlugin {
    @Override protected String getLifecycleTaskName() {
        return 'testIde'
    }

    @Override protected void onApply(Project target) {
        def worker = target.task('generateXml')
        addWorker(target.getTasks().named(worker.getName()), worker.getName());
    }
}
