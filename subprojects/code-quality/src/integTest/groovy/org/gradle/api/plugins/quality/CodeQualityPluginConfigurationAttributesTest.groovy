/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CodeQualityPluginConfigurationAttributesTest extends AbstractIntegrationSpec {

    def "plugin runtime configuration is configured as runtime classpath"() {
        given: "a multiproject build"
        settingsFile << """
            include("producer", "consumer")
        """

        and: "a library subproject with applied code quality plugin, which implicitly declares a configuration matching a plugin name"
        file("producer/build.gradle") << """
            plugins {
                id("java-library")
                id("$plugin")
            }
            ${mavenCentralRepository()}
            dependencies {
                implementation(localGroovy())
            }
        """

        and: "a library subproject that depends on the other subproject"
        file("consumer/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(":producer"))
            }
        """

        when: "fails to resolve :consumer:testRuntimeClasspath because outgoing variants from both the " +
            "library and plugin configurations have attributes that define a runtime classpath"
        fails("test")

        then: "error description explains the ambiguity"
        failureDescriptionContains("Could not determine the dependencies of task ':consumer:test'.")
        failureCauseContains("Could not resolve all task dependencies for configuration ':consumer:testRuntimeClasspath'.")
        failureCauseContains("However we cannot choose between the following variants of project :producer:")
        failureCauseContains("- $plugin")
        failureCauseContains("- runtimeElements")

        where:
        plugin << ['codenarc', 'pmd', 'checkstyle']
    }

}
