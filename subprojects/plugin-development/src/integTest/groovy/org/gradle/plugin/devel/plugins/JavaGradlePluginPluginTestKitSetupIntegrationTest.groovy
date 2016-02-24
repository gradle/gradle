/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.TextUtil.normaliseFileAndLineSeparators

class JavaGradlePluginPluginTestKitSetupIntegrationTest extends AbstractIntegrationSpec {

    private static final String PLUGIN_CLASSPATH_TASK_PATH = ":$JavaGradlePluginPlugin.PLUGIN_CLASSPATH_TASK_NAME"

    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
        """
    }

    def "configures functional testing by conventions"() {
        when:
        succeeds 'build'

        then:
        result.executedTasks.contains(PLUGIN_CLASSPATH_TASK_PATH)
        File classpathManifest = file("build/$JavaGradlePluginPlugin.PLUGIN_CLASSPATH_TASK_NAME/plugin-classpath.txt")
        classpathManifest.exists() && classpathManifest.isFile()
        classpathManifest.text.contains(normaliseFileAndLineSeparators(file('build/classes/main').absolutePath))
        classpathManifest.text.contains(normaliseFileAndLineSeparators(file('build/resources/main').absolutePath))
    }

    def "can configure plugin and test source set by extension"() {
        given:
        buildFile << """
            sourceSets.remove(sourceSets.main)

            sourceSets {
                customMain {
                    java {
                        srcDir 'src'
                    }
                    resources {
                        srcDir 'resources'
                    }
                }

                functionalTest {
                    java {
                        srcDir 'src/functional/java'
                    }
                    resources {
                        srcDir 'src/functional/resources'
                    }
                }
            }

            task functionalTest(type: Test) {
                testClassesDir = sourceSets.functionalTest.output.classesDir
                classpath = sourceSets.functionalTest.runtimeClasspath
            }

            check.dependsOn functionalTest

            javaGradlePlugin {
                functionalTestClasspath {
                    pluginSourceSet sourceSets.customMain
                    testSourceSets sourceSets.functionalTest
                }
            }
        """

        when:
        succeeds 'build'

        then:
        result.executedTasks.contains(PLUGIN_CLASSPATH_TASK_PATH)
        File classpathManifest = file("build/$JavaGradlePluginPlugin.PLUGIN_CLASSPATH_TASK_NAME/plugin-classpath.txt")
        classpathManifest.exists() && classpathManifest.isFile()
        classpathManifest.text.contains(normaliseFileAndLineSeparators(file('build/classes/customMain').absolutePath))
        classpathManifest.text.contains(normaliseFileAndLineSeparators(file('build/resources/customMain').absolutePath))
    }
}
