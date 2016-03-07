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
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.GUtil

import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME
import static org.gradle.plugin.devel.plugins.internal.tasks.PluginUnderTestMetadata.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static org.gradle.plugin.devel.plugins.internal.tasks.PluginUnderTestMetadata.METADATA_FILE_NAME
import static org.gradle.util.TextUtil.normaliseFileAndLineSeparators

class JavaGradlePluginPluginTestKitSetupIntegrationTest extends AbstractIntegrationSpec {

    private static final String PLUGIN_UNDER_TEST_METADATA_TASK_PATH = ":$PLUGIN_UNDER_TEST_METADATA_TASK_NAME"

    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
        """
    }

    def "wires creation of plugin under test metadata into build lifecycle"() {
        given:
        def module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('compile', module)

        when:
        succeeds 'build'

        then:
        executedAndNotSkipped PLUGIN_UNDER_TEST_METADATA_TASK_PATH
        def pluginMetadata = file("build/$PLUGIN_UNDER_TEST_METADATA_TASK_NAME/$METADATA_FILE_NAME")
        def expectedClasspath = [file('build/classes/main'), file('build/resources/main'), module.artifactFile]
        assertImplementationClasspath(pluginMetadata, expectedClasspath)
    }

    def "can configure plugin and test source set by extension"() {
        given:
        buildFile << """
            sourceSets.remove(sourceSets.main)

            sourceSets {
                custom {
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

            gradlePlugin {
                pluginSourceSet sourceSets.custom
                testSourceSets sourceSets.functionalTest
            }
        """
        def module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('customCompile', module)

        when:
        succeeds 'build'

        then:
        executedAndNotSkipped PLUGIN_UNDER_TEST_METADATA_TASK_PATH
        def pluginMetadata = file("build/$PLUGIN_UNDER_TEST_METADATA_TASK_NAME/$METADATA_FILE_NAME")
        def expectedClasspath = [file('build/classes/custom'), file('build/resources/custom'), module.artifactFile]
        assertImplementationClasspath(pluginMetadata, expectedClasspath)
    }

    private String compileDependency(String configurationName, MavenModule module) {
        """
            repositories {
                maven { url "$mavenRepo.uri" }
            }

            dependencies {
                $configurationName '$module.groupId:$module.artifactId:$module.version'
            }
        """
    }

    static assertImplementationClasspath(File classpathManifest, List<File> implementationClasspath) {
        assert classpathManifest.exists() && classpathManifest.isFile()
        String loadedImplementationClasspath = GUtil.loadProperties(classpathManifest).getProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY)
        assert !loadedImplementationClasspath.contains("\\")

        implementationClasspath.each { file ->
            assert loadedImplementationClasspath.contains(normaliseFileAndLineSeparators(file.absolutePath))
        }
    }
}
