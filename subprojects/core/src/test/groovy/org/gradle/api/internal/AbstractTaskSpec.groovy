/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.junit.Assert.assertTrue

class AbstractTaskSpec extends AbstractProjectBuilderSpec {
    def instantiator = TestUtil.instantiatorFactory().decorateLenient()

    static class TestTask extends DefaultTask {
    }

    TestTask createTask(String name) {
        def task = TestUtil.create(temporaryFolder).createTask(TestTask, project, name)
        assertTrue(TestTask.isAssignableFrom(task.getClass()))
        return task
    }

    def "can add action to a task via Task.getActions() List"() {
        setup:
        def task = createTask("task")
        when:
        def actions = task.actions
        and:
        def action = Mock(Action)

        actions.add(action)
        then:
        task.actions.size() == 1
        actions.size() == 1
    }

    def "can detect tasks with custom actions added"() {
        when:
        def task = createTask("task")

        then:
        !task.hasCustomActions

        when:
        task.prependParallelSafeAction {}

        then:
        !task.hasCustomActions

        when:
        task.doFirst {}

        then:
        task.hasCustomActions
    }
}
