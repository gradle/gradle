/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugin.devel.variants

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class UpgradeGradlePluginTransformsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildTestFixture.withBuildInSubDir()

        singleProjectBuild("old-plugin") {
            file("src/main/java/com/example/OldPlugin.java").java """
                package com.example;

                import org.gradle.api.*;

                public class OldPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        System.out.println("Hello from old plugin");
                    }
                }
            """
            buildFile """
                apply plugin: 'java-gradle-plugin'

                gradlePlugin {
                    plugins {
                        greeting {
                            id = 'com.example.old-plugin'
                            implementationClass = 'com.example.OldPlugin'
                        }
                    }
                }
            """
        }

        settingsFile << """
            pluginManagement {
                includeBuild("old-plugin")
            }
            rootProject.name = "consumer"
        """
        buildFile """
            plugins {
                id 'com.example.old-plugin'
            }
        """
    }

    def "can upgrade old plugins to latest with transforms"() {
//        when:
//        succeeds(":updateable-plugin:outgoingVariants", ":old-plugin:outgoingVariants")
//        then:
//        noExceptionThrown()

        when:
        succeeds("help")
        then:
        outputContains("Hello from old plugin")
    }
}
