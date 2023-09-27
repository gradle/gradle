/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r62

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.util.GradleVersion
import org.junit.Assume
import spock.lang.Issue

class ToolingApiIdeaLocalGroovyCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/12274")
    @TargetGradleVersion(">=3.4")
    def "resolves localGroovy() as IdeaSingleEntryLibraryDependency for IdeaProject via tooling API"() {
        given:
        Assume.assumeFalse("6.2 is broken and does not include localGroovy in the IdeaModule", targetVersion == GradleVersion.version("6.2"))
        settingsFile << "rootProject.name = 'root'"

        buildFile << """
        plugins {
            id 'java-library'
        }

        dependencies {
            implementation(localGroovy())
        }
        """

        when:
        IdeaProject ideaProject = toolingApi.withConnection { connection -> connection.getModel(IdeaProject) }

        then:
        def ideaModule = ideaProject.modules.find { it.name == 'root' }
        def dependency = ideaModule.dependencies.find { it.file.name.contains("groovy-") }
        dependency instanceof IdeaSingleEntryLibraryDependency
    }
}
