/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Verifies that {@code JacocoTaskExtension.destinationFile} (a {@link org.gradle.api.file.RegularFileProperty})
 * can be configured from the Kotlin DSL.
 */
class JacocoTaskExtensionKotlinDslIntegrationTest extends AbstractIntegrationSpec {

    def "destinationFile can be configured from the Kotlin DSL via #description"() {
        given:
        settingsKotlinFile << 'rootProject.name = "jacoco-kotlin"'
        buildKotlinFile << """
            import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

            plugins {
                java
                jacoco
            }

            tasks.test {
                extensions.configure<JacocoTaskExtension>("jacoco") {
                    $configuration
                }
            }

            tasks.register("verifyDestinationFile") {
                val destinationFile = tasks.test.flatMap {
                    it.extensions.getByType(JacocoTaskExtension::class.java).destinationFile
                }
                doLast {
                    println("JACOCO_DESTINATION=" + destinationFile.get().asFile.name)
                }
            }
        """

        when:
        succeeds "verifyDestinationFile"

        then:
        outputContains("JACOCO_DESTINATION=custom.exec")

        where:
        description                          | configuration
        "assignment of a RegularFile provider" | 'destinationFile = layout.buildDirectory.file("jacoco/custom.exec")'
        "assignment of a File"                 | 'destinationFile = file("build/jacoco/custom.exec")'
        ".set() of a RegularFile provider"     | 'destinationFile.set(layout.buildDirectory.file("jacoco/custom.exec"))'
    }
}
