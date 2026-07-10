/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.test.fixtures.AbstractProjectBuilderSpec;
import org.gradle.util.TestUtil;

/**
 * Tests for the {@link HelpTasksPlugin}.
 */
class HelpTasksPluginSpec extends AbstractProjectBuilderSpec {
    def "tasks description reflects whether project has sub-projects or not"() {
        given:
        def child = TestUtil.createChildProject(project, "child")

        when:
        project.pluginManager.apply(HelpTasksPlugin)
        child.pluginManager.apply(HelpTasksPlugin)

        then:
        project.tasks[ProjectInternal.TASKS_TASK].description == "Displays the tasks runnable from root project 'test-project' (some of the displayed tasks may belong to subprojects)."
        child.tasks[ProjectInternal.TASKS_TASK].description == "Displays the tasks runnable from project ':child'."
    }
}
