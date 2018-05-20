/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

/**
 * Common behaviour tests for all IDE plugins dealing with multiple builds (buildSrc, composite builds).
 */
abstract class AbstractMultiBuildIdeIntegrationTest extends AbstractIntegrationSpec {
    abstract String getPluginId()
    abstract String getWorkspaceTask()
    abstract IdeWorkspaceFixture workspace(TestFile workspaceDir)

    @Issue("https://github.com/gradle/gradle/issues/5110")
    def "buildSrc project can apply IDE plugin"() {
        file("buildSrc/build.gradle") << """
            apply plugin: '${pluginId}'
            tasks.build.dependsOn tasks.${workspaceTask}
        """

        expect:
        succeeds()
        workspace(file("buildSrc")).assertExists()
    }
}
