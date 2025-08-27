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

package org.gradle.launcher.daemon.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.launcher.daemon.ToolchainPropertiesDeprecationsFixture

class ToolchainPropertiesIntegrationTest extends AbstractIntegrationSpec implements ToolchainPropertiesDeprecationsFixture {
    def "nags when toolchain property is specified as a project property on the command line"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        args("-P${ToolchainConfiguration.AUTO_DETECT}=false")

        then:
        expectToolchainPropertyDeprecationFor('org.gradle.java.installations.auto-detect', 'false')
        executer.withToolchainDetectionEnabled()
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "nags when toolchain property is specified as a Gradle property in project gradle.properties and as a project property on the command line"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        file("gradle.properties") << "${ToolchainConfiguration.AUTO_DETECT}=true"
        args("-P${ToolchainConfiguration.AUTO_DETECT}=false")

        then:
        expectToolchainPropertyDeprecationFor('org.gradle.java.installations.auto-detect', 'false')
        executer.withToolchainDetectionEnabled()
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "does not nag when toolchain property is specified as a Gradle property on the command line"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        args("-D${ToolchainConfiguration.AUTO_DETECT}=false")

        then:
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "does not nag when toolchain property is specified as a Gradle property in project gradle.properties"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        file("gradle.properties") << "${ToolchainConfiguration.AUTO_DETECT}=false"

        then:
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "does not nag when toolchain property is specified as a Gradle property in gradle user home gradle.properties"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        executer.requireOwnGradleUserHomeDir("so we can set properties in GUH/gradle.properties")
        file("user-home/gradle.properties") << "${ToolchainConfiguration.AUTO_DETECT}=false"

        then:
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "does not nag when toolchain property is specified as both a Gradle property and a project property on the command line"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        args("-D${ToolchainConfiguration.AUTO_DETECT}=false", "-P${ToolchainConfiguration.AUTO_DETECT}=false")

        then:
        succeeds("printProperty")

        and:
        outputContains("Project property '${ToolchainConfiguration.AUTO_DETECT}': false")
        outputContains("Toolchain auto-detect enabled: false")
    }

    def "sensible error when toolchain property is specified as both a Gradle property and a project property on the command line and the values differ"() {
        given:
        settingsFile << "rootProject.name = 'test'"
        buildFile << printProjectProperty(ToolchainConfiguration.AUTO_DETECT)

        when:
        args("-D${ToolchainConfiguration.AUTO_DETECT}=false", "-P${ToolchainConfiguration.AUTO_DETECT}=true")

        then:
        fails("printProperty")

        and:
        failure.assertHasCause("The Gradle property 'org.gradle.java.installations.auto-detect' (set to 'false') has a different value than the project property 'org.gradle.java.installations.auto-detect' (set to 'true')." +
            " Please set them to the same value or only set the Gradle property.")
    }

    def printProjectProperty(String property) {
        return """
            tasks.register("printProperty") {
                def property = project.findProperty('$property')
                def config = project.services.get(org.gradle.jvm.toolchain.internal.ToolchainConfiguration).isAutoDetectEnabled()
                doLast {
                    println("Project property '$property': \${property}")
                    println("Toolchain auto-detect enabled: \${config}")
                }
            }
        """
    }
}
