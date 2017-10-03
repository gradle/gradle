/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

//These tests depend on https://plugins.gradle.org
@Requires(TestPrecondition.ONLINE)
@LeaksFileHandles
class DeployedPortalIntegrationSpec extends AbstractIntegrationSpec {

    private final static String HELLO_WORLD_PLUGIN_ID = "org.gradle.hello-world"
    private final static String HELLO_WORLD_PLUGIN_VERSION = "0.2"

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "Can access plugin classes when resolved but not applied"() {
        when:
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION" apply false
            }

            task customHello(type: org.gradle.plugin.HelloWorldTask)
        """

        then:
        succeeds("customHello")

        and:
        output.contains("Hello World!")

        and:
        fails("helloWorld")
    }

    def "Can apply plugins to subprojects"() {
        when:
        settingsFile << """
            include 'sub'
        """
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION" apply false
            }

            subprojects {
                apply plugin: "$HELLO_WORLD_PLUGIN_ID"
            }
        """

        then:
        succeeds("sub:helloWorld")

        and:
        output.contains("Hello World!")

    }

    def "can resolve and apply a plugin from portal"() {
        when:
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """

        then:
        succeeds("helloWorld")

        and:
        output.contains("Hello World!")
    }

    def "resolving a non-existing plugin results in an informative error message"() {
        when:
        buildScript """
            plugins {
                id "org.gradle.non-existing" version "1.0"
            }
        """

        then:
        fails("dependencies")

        and:
        failureDescriptionStartsWith("Plugin [id 'org.gradle.non-existing' version '1.0'] was not found in any of the following sources:")
        failureDescriptionContains("- Gradle Central Plugin Repository (no 'org.gradle.non-existing' plugin available - see https://plugins.gradle.org for available plugins)")
    }
}
