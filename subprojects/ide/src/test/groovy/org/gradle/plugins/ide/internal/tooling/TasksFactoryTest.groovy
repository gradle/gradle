/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.plugins.ide.internal.tooling.eclipse.DefaultEclipseProject
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class TasksFactoryTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    final Project project = Mock()
    final def eclipseProject = new DefaultEclipseProject(null, null, null, null, [])
    final task = TestUtil.create(temporaryFolder).task(DefaultTask)

    def "does not return tasks"() {
        TasksFactory factory = new TasksFactory(false)

        when:
        factory.allTasks = [:]
        factory.allTasks.put(project, [task] as Set)
        def tasks = factory.getTasks(project)

        then:
        tasks.empty
    }

    def "returns tasks"() {
        TasksFactory factory = new TasksFactory(true)

        when:
        factory.allTasks = [:]
        factory.allTasks.put(project, [task] as Set)
        def tasks = factory.getTasks(project)

        then:
        tasks.size() == 1
    }
}
