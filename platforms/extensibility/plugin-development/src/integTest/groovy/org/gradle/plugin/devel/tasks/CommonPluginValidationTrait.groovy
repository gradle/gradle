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

package org.gradle.plugin.devel.tasks

import org.gradle.internal.reflect.validation.ValidationMessageDisplayConfiguration
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.api.problems.Severity.ERROR
import static org.gradle.api.problems.Severity.WARNING

trait CommonPluginValidationTrait {
    static <T extends ValidationMessageDisplayConfiguration> AbstractPluginValidationIntegrationSpec.DocumentedProblem error(T message, String id = "incremental_build", String section = "") {
        new AbstractPluginValidationIntegrationSpec.DocumentedProblem(message, ERROR, id, section)
    }

    static <T extends ValidationMessageDisplayConfiguration> AbstractPluginValidationIntegrationSpec.DocumentedProblem warning(T message, String id = "incremental_build", String section = "") {
        new AbstractPluginValidationIntegrationSpec.DocumentedProblem(message, WARNING, id, section)
    }

    static <T extends ValidationMessageDisplayConfiguration> AbstractPluginValidationIntegrationSpec.DocumentedProblem warning(String message, String id = "incremental_build", String section = "") {
        new AbstractPluginValidationIntegrationSpec.DocumentedProblem(message, WARNING, id, section)
    }

    TestFile getJavaTaskSource() {
        source("src/main/java/MyTask.java")
    }

    TestFile getGroovyTaskSource() {
        buildFile  """
            apply plugin: "groovy"
        """
        source("src/main/groovy/MyTask.groovy")
    }

    TestFile getKotlinTaskSource() {
        buildFile.delete()
        buildKotlinFile << """
            plugins {
                id("java-gradle-plugin")
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
            }
        """
        source("src/main/kotlin/MyTask.kt")
    }
}
