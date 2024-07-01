/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.experiments;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

class CapabilitiesConflictsAndTheDefaultCapability extends AbstractIntegrationSpec {
    def "can depend on two configurations from the same project, sharing the default capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "conf1")
                myDeps project(path: ":producer-with-default-capability", configuration: "conf2")
            }
        """

        when:
        succeeds("doResolve")

        then:
        file("build/resolved/file-1.txt").assertExists()
        file("build/resolved/file-2.txt").assertExists()

        when:
        succeeds(":producer-with-default-capability:outgoingVariants")

        then:
        result.assertOutputContains("""
--------------------------------------------------
Variant conf1
--------------------------------------------------

Capabilities
    - example:producer-with-default-capability:unspecified (default capability)""")
        result.assertOutputContains("""
--------------------------------------------------
Variant conf2
--------------------------------------------------

Capabilities
    - example:producer-with-default-capability:unspecified (default capability)""")
    }

    def setup() {
        settingsFile << """
            rootProject.name = "example"
            include "producer-with-default-capability"
        """

        file("producer-with-default-capability/file-1.txt").text = "file 1 from producer-with-default-capability"
        file("producer-with-default-capability/file-2.txt").text = "file 2 from producer-with-default-capability"

        file("producer-with-default-capability/build.gradle") << """
            configurations {
                consumable("conf1")
                consumable("conf2")
            }
            artifacts {
                conf1 file("file-1.txt")
                conf2 file("file-2.txt")
            }
        """

        buildFile << """
            configurations {
                dependencyScope("myDeps")
                resolvable("resolveMe") {
                    extendsFrom myDeps
                }
            }

            task doResolve(type: Sync) {
                from(configurations.resolveMe)
                into("build/resolved")
            }
        """
    }
}
