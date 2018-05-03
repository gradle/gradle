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

package org.gradle.buildinit.plugins.internal.action

import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.buildinit.tasks.InitBuild
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildInitAutoApplyActionSpec extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    TestFile buildFile
    ProjectInternal projectInternal
    DefaultPluginManager pluginManager
    TaskContainerInternal taskContainerInternal

    public void setup() {
        projectInternal = Mock(ProjectInternal)
        taskContainerInternal = Mock(TaskContainerInternal)
        pluginManager = Mock(DefaultPluginManager)
        _ * projectInternal.getTasks() >> taskContainerInternal

    }

    def "applies placeholder action for init on taskcontainer"() {
        when:
        new BuildInitAutoApplyAction().execute(projectInternal)

        then:
        1 * taskContainerInternal.addPlaceholderAction("init", InitBuild.class, _)
        1 * projectInternal.getParent() >> null
    }

    def "is not applied on non rootprojects"() {
        given:
        isNotRootProject()
        when:
        new BuildInitAutoApplyAction().execute(projectInternal)

        then:
        0 * taskContainerInternal.addPlaceholderAction(*_)
    }

    def isNotRootProject() {
        projectInternal.getParent() >> Mock(ProjectInternal)
    }

    void noChildprojects() {
        projectInternal.subprojects >> []
    }
}
