/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ApiUpgradeReportIntegrationTest extends AbstractIntegrationSpec {

    private static final File ACCEPTED_TEST_CHANGES = new File("src/integTest/resources/org/gradle/api/internal/upgrade/report/ApiUpgradeReportIntegrationTest/accepted-public-api-changes.json")

    def "can report upgrades for Kotlin 1.6.21"() {
        settingsFile """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
        """
        buildScript """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.6.21'
                id 'java'
            }
            repositories {
                mavenCentral()
            }

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = sourceCompatibility
            }
        """
        file("src/main/kotlin/MyClass.kt") << """
            class MyClass {}
        """
        file("src/main/java/MyClass2.java") << """
            class MyClass2 {}
        """

        when:
        run("assemble", "-Pkotlin.parallel.tasks.in.project=true", "--info", "-Dorg.gradle.binary.upgrade.report.json=${ACCEPTED_TEST_CHANGES.getAbsolutePath()}")
        then:
        executedAndNotSkipped(":assemble")
    }

}
