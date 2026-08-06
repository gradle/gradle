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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile

trait CommonPluginValidationTrait {
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
        def settings = file("settings.gradle")
        def existingSettings = settings.exists() ? settings.text : ""
        settings.text = """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    ${RepoScriptBlockUtil.kotlinDevRepositoryDefinition(GradleDsl.GROOVY)}
                }
            }
        """ + existingSettings
        buildKotlinFile << """
            plugins {
                id("java-gradle-plugin")
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
                ${RepoScriptBlockUtil.kotlinDevRepositoryDefinition(GradleDsl.KOTLIN)}
            }
        """
        source("src/main/kotlin/MyTask.kt")
    }
}
