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

package org.gradle.smoketests

import org.gradle.internal.reflect.validation.ValidationMessageChecker

class ProguardSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {
    @Override
    Map<String, Versions> getPluginsToValidate() {
        return ['proguard': Versions.of("")]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("buildSrc/src/main/groovy/proguard.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                google()
                mavenCentral()
            }

            dependencies {
                implementation 'com.guardsquare:proguard-gradle:${TestedVersions.proguardGradle}'
            }

            // Configure the validation task here, since there is no ProGuard plugin
            def validationTask = tasks.register('validatePluginWithId_proguard', ValidatePlugins) {
                outputFile = project.layout.buildDirectory.file("reports/plugins/validation-report-for-proguard.txt")
                classes.from({ ->
                    // Only test this one task, since analyzing all the classes seems to cause some problems
                    configurations.runtimeClasspath.files.collect { project.zipTree(it).matching { include "**/ProGuardTask.*" } }
                })
                classpath = configurations.runtimeClasspath
            }

            tasks.named("validateExternalPlugins") {
                dependsOn(validationTask)
            }
        """
        validatePlugins {
            onPlugin("ProguardPlugin") {
                passes()
            }
            onPlugin("proguard") {
                passes()
            }
        }
    }
}
