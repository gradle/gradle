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

package org.gradle.integtests.resolve.platforms


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Integration tests for {@link org.gradle.api.plugins.JavaPlatformPlugin}.
 */
class JavaPlatformPluginIntegrationTest extends AbstractIntegrationSpec {

    def "consumable configurations are not realized at configuration-time"() {
        given:
        buildFile("""
            plugins {
                id("java-platform")
            }

            configurations.named(${configuration}).configure {
                throw new RuntimeException("Should not be called!")
            }
        """)

        expect:
        succeeds("help")

        where:
        configuration << [
            "JavaPlatformPlugin.API_ELEMENTS_CONFIGURATION_NAME",
            "JavaPlatformPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME",
            "JavaPlatformPlugin.ENFORCED_API_ELEMENTS_CONFIGURATION_NAME",
            "JavaPlatformPlugin.ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME",
        ]
    }

}
