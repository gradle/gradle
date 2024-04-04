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

package org.gradle.smoketests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.SmokeTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

/**
 * Smoke test verifying the external plugins used during the Gradle build itself.
 *
 * We validate the plugins during runtime, too. However, this test ensures that we learn about failures early,
 * i.e. the test is important since it shows if we need to change something in our build when we do the next
 * major version bump. Either by having the external plugins been fixed or by switching to different plugins.
 */
@Requires([
    UnitTestPreconditions.Jdk9OrLater,
    IntegTestPreconditions.NotConfigCached,
    SmokeTestPreconditions.GradleBuildJvmSpecAvailable
])
class GradleBuildExternalPluginsValidationSmokeTest extends AbstractGradleceptionSmokeTest implements WithPluginValidation, ValidationMessageChecker {

    def setup() {
        allPlugins.projectPathToBuildDir = new GradleBuildDirLocator()
    }

    def "performs static validation of plugins used by the Gradle build"() {
        when:
        allPlugins.passing { true }

        then:
        allPlugins.performValidation([
            "--no-parallel" // make sure we have consistent execution ordering as we skip cached tasks
        ])
    }

    private static class GradleBuildDirLocator implements ProjectBuildDirLocator {

        private Map<String, String> subprojects

        @Override
        TestFile getBuildDir(String projectPath, TestFile projectRoot) {
            if (projectPath == ':') {
                return projectRoot.file("build")
            } else {
                if (subprojects == null) {
                    ArrayNode arr = (ArrayNode) projectRoot.file(".teamcity/subprojects.json").withInputStream {
                        new ObjectMapper().readTree(it)
                    }
                    subprojects = [:]
                    for (int i = 0; i < arr.size(); i++) {
                        JsonNode node = arr.get(i)
                        subprojects.put(node.get("name").asText(), node.get("path").asText())
                    }
                }

                assert projectPath.startsWith(":")
                projectPath = projectPath.substring(1)
                assert !projectPath.contains(":")

                def path = subprojects.get(projectPath)
                if (path == null) {
                    throw new IllegalArgumentException("Cannot find build dir for project path '$projectPath'")
                }
                return projectRoot.file(path).file("build")
            }
        }
    }
}
