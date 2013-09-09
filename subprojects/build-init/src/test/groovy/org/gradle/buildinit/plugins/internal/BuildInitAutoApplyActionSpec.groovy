/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.plugins.PluginContainer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildInitAutoApplyActionSpec extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    TestFile buildFile
    ProjectInternal projectInternal
    PluginContainer pluginContainer
    TaskContainerInternal taskContainerInternal

    public void setup() {
        projectInternal = Mock(ProjectInternal)
        pluginContainer = Mock(PluginContainer)
        taskContainerInternal = Mock(TaskContainerInternal)
        _ * projectInternal.getTasks() >> taskContainerInternal

    }

    def "applies placeholder action for init on taskcontainer"() {
        when:
        new BuildInitAutoApplyAction().execute(projectInternal)
        then:
        1 * taskContainerInternal.addPlaceholderAction("init", _) >> {args -> args[1].run()}
        1 * projectInternal.getParent() >> null
        1 * projectInternal.getPlugins() >> pluginContainer
        1 * pluginContainer.apply("build-init")
    }

    def "is not applied on non rootprojects"() {
        given:
        isNotRootProject()
        when:
        new BuildInitAutoApplyAction().execute(projectInternal)
        then:
        0 * taskContainerInternal.addPlaceholderAction("init", _)
        0 * projectInternal.getPlugins() >> pluginContainer
        0 * pluginContainer.apply("build-init")
    }

    def isNotRootProject() {
        projectInternal.getParent() >> Mock(ProjectInternal)
    }

    void noChildprojects() {
        projectInternal.subprojects >> []
    }
}
