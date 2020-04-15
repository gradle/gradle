/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.Plugin
import org.gradle.api.Project

class InstantExecutionUndeclaredBuildInputsIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    def "reports undeclared use of system property from buildSrc plugin with Java implementation"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    String isCi = System.getProperty("CI");
                }
            }
        """
        buildFile << """
            apply plugin: SneakyPlugin
        """

        when:
        instantRun()

        then:
        outputContains("=> get property 'CI' from SneakyPlugin")
    }
}
