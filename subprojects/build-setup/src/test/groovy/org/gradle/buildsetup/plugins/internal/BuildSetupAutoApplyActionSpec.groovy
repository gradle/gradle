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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.PluginContainer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildSetupAutoApplyActionSpec extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    TestFile buildFile
    ProjectInternal projectInternal
    PluginContainer pluginContainer
    GradleInternal gradle

    public void setup() {
        buildFile = temporaryFolder.file("build.gradle")
        projectInternal = Mock(ProjectInternal)
        pluginContainer = Mock(PluginContainer)
        gradle = Mock(GradleInternal)
        _ * projectInternal.getGradle() >> gradle
        _ * projectInternal.getBuildFile() >> buildFile
    }

    def "is not applied if build file exists"() {
        given:
        buildFileExists()
        noChildprojects()
        when:
        new BuildSetupAutoApplyAction().execute(projectInternal)
        then:
        0 * projectInternal.getPlugins() >> pluginContainer
        0 * pluginContainer.apply("build-setup")
    }

    def "is not applied on non rootprojects"() {
        given:
        isNotRootProject()
        when:
        new BuildSetupAutoApplyAction().execute(projectInternal)
        then:
        0 * projectInternal.getPlugins() >> pluginContainer
        0 * pluginContainer.apply("build-setup")
    }

    def "is not applied on multproject"() {
        given:
        hasChildprojects()
        when:
        new BuildSetupAutoApplyAction().execute(projectInternal)
        then:
        0 * projectInternal.getPlugins() >> pluginContainer
        0 * pluginContainer.apply("build-setup")
    }

    def isNotRootProject() {
        projectInternal.getParent() >> Mock(ProjectInternal)
    }

    void noChildprojects() {
        projectInternal.subprojects >> []
    }

    def hasChildprojects() {
        projectInternal.subprojects >> [Mock(ProjectInternal)]
    }

    def buildFileExists() {
        buildFile.createFile()
    }
}
