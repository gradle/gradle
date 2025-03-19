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

package org.gradle.performance

import org.gradle.profiler.BuildMutator
import org.gradle.profiler.ScenarioContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.VersionNumber

class InjectDevelocityPlugin implements BuildMutator {
    private static final VersionNumber DEVELOCITY_PLUGIN_RENAME_VERSION = VersionNumber.parse("3.17")

    final File projectDir
    final String develocityPluginVersion
    final boolean isDevelocity

    InjectDevelocityPlugin(File projectDir, String develocityPluginVersion) {
        this.projectDir = projectDir
        this.develocityPluginVersion = develocityPluginVersion
        this.isDevelocity = VersionNumber.parse(develocityPluginVersion).baseVersion >= DEVELOCITY_PLUGIN_RENAME_VERSION
        println "InjectDevelocityPlugin develocityPluginVersion = $develocityPluginVersion"
    }

    private String getDevelocityPluginArtifactName() {
        isDevelocity ? 'develocity-gradle-plugin' : 'gradle-enterprise-gradle-plugin'
    }

    private String getDevelocityPluginId() {
        isDevelocity ? 'com.gradle.develocity' : 'com.gradle.enterprise'
    }

    @Override
    void beforeScenario(ScenarioContext context) {
        def projectTestDir = new TestFile(projectDir)
        def settingsScript = projectTestDir.file('settings.gradle')
        settingsScript.text = """
                buildscript {
                    repositories {
                        maven {
                            name = 'gradleInternalRepository'
                            url = '${System.getenv("GRADLE_INTERNAL_REPO_URL")}/enterprise-libs-snapshots-local/'
                            credentials {
                                username = System.getenv("GRADLE_INTERNAL_REPO_USERNAME")
                                password = System.getenv("GRADLE_INTERNAL_REPO_PASSWORD")
                            }
                            authentication {
                                basic(BasicAuthentication)
                            }
                        }
                    }

                    dependencies {
                        classpath "com.gradle:${develocityPluginArtifactName}:${develocityPluginVersion}"
                    }
                }

                if (System.getProperty('enableScan')) {
                    apply plugin: '${develocityPluginId}'
                }
                """ + settingsScript.text
    }
}
