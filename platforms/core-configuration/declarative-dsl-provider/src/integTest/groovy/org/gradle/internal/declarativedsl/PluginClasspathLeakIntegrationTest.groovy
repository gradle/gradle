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

package org.gradle.internal.declarativedsl

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class PluginClasspathLeakIntegrationTest extends AbstractIntegrationSpec {

    def "libraries from the Gradle distribution don't leak into the plugin classpath"() {
        given:
        publishedPluginWhichExecutesCode(
            "group", "artifact", "1.0",
            """System.out.println("jar = " + new File(kotlinx.serialization.modules.SerializersModuleKt.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getName());"""
        )

        buildFile << """
            buildscript {
                dependencies {
                    classpath 'group:artifact:1.0'
                }

                repositories {
                    maven {
                        url = "${mavenRepo.uri}"
                    }
                    mavenCentral()
                }
            }
            apply plugin: 'printer'
        """

        when:
        succeeds("help")

        then:
        // we check that the kotlinx.serialization jar has been loaded from the actual plugin dependency (v1.5.1)
        // and not from the kotlinx.serialization dependency in the Gradle distribution, which is version 1.6.2 or higher
        outputContains("jar = kotlinx-serialization-core-jvm-1.5.1")
    }

    private void publishedPluginWhichExecutesCode(String group, String artifact, String version, String code) {
        def pluginProjectDir = file("plugin")

        addPluginSettingsFile(pluginProjectDir)
        addPluginSource(pluginProjectDir, code)
        addPluginBuildFile(pluginProjectDir, group, version, artifact)

        executer.inDirectory(pluginProjectDir).withTasks("publishAllPublicationsToRepoRepository").run()
    }

    private TestFile addPluginBuildFile(TestFile pluginProjectDir, String group, String version, String artifact) {
        pluginProjectDir.file("build.gradle") <<
            """
                apply plugin: 'java-gradle-plugin'
                apply plugin: 'groovy'
                apply plugin: 'maven-publish'

                group = '${group}'
                version = '${version}'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    api 'org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1'
                    api 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1'
                }

                gradlePlugin {
                    plugins {
                        'printer' { id='printer'; implementationClass='org.gradle.test.TestPlugin' }
                    }
                }

                publishing {
                    publications {
                        maven(MavenPublication) {
                            artifactId = '${artifact}'
                            from components.java
                        }
                    }
                    repositories {
                        maven {
                            name = 'repo'
                            url = '${mavenRepo.uri}'
                        }
                    }
                }
            """
    }

    private static addPluginSettingsFile(TestFile pluginProjectDir) {
        pluginProjectDir.file('settings.gradle') << ""
    }

    private static void addPluginSource(TestFile pluginProjectDir, String code) {
        def packageName = "org.gradle.test"
        def sourceFilePath = "${packageName.replaceAll("\\.", "/")}/${"TestPlugin"}.groovy"
        pluginProjectDir.file("src/main/groovy/${sourceFilePath}") << """
            ${packageName ? "package $packageName" : ""}

            class TestPlugin implements $Plugin.name<$Project.name> {
                void apply($Project.name project) {
                    ${code}
                }
            }
        """
    }

}
