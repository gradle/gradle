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

    def "can upgradle Kotlin 1.6.21"() {
        def buildSrc = testDirectory.file("buildSrc")
        buildSrc.file("build.gradle") << """
plugins {
    id 'groovy-gradle-plugin'
}

repositories {
    gradlePluginPortal()
}"""
        buildSrc.file("src/main/groovy").mkdirs()
        buildSrc.file("src/main/groovy/commons.java-conventions.gradle") << """
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

tasks.named('test') {
    useJUnitPlatform()
}
"""


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
            }
            repositories {
                mavenCentral()
            }
        """
        file("src/main/kotlin/MyClass.kt") << """
            class MyClass {}
        """
        file("src/main/java/MyJavaClass.java") << "public class MyJavaClass {}"

        when:
        run("compileKotlin", "-Pkotlin.parallel.tasks.in.project=true", "--info")
        then:
        executedAndNotSkipped(":compileKotlin")
    }

}
