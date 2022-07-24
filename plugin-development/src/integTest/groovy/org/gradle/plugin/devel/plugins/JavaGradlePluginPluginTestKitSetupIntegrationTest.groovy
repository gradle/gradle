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
import org.gradle.util.internal.GUtil

import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME
import static org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.METADATA_FILE_NAME
import static org.gradle.util.internal.TextUtil.normaliseFileAndLineSeparators

class JavaGradlePluginPluginTestKitSetupIntegrationTest extends AbstractIntegrationSpec {

    private static final String PLUGIN_UNDER_TEST_METADATA_TASK_PATH = ":$PLUGIN_UNDER_TEST_METADATA_TASK_NAME"

    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
        """
    }

    def "has default conventions"() {
        buildFile << """
            task assertHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.test.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }
        """
        expect:
        succeeds("assertHasTestKit")
        succeeds("test")
        result.assertTaskExecuted(":pluginUnderTestMetadata")
        result.assertTaskExecuted(":pluginDescriptors")
    }

    def "wires creation of plugin under test metadata into build lifecycle"() {
        given:
        def module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('implementation', module)

        when:
        succeeds 'build'

        then:
        executedAndNotSkipped PLUGIN_UNDER_TEST_METADATA_TASK_PATH
        def pluginMetadata = file("build/$PLUGIN_UNDER_TEST_METADATA_TASK_NAME/$METADATA_FILE_NAME")
        def expectedClasspath = [file('build/classes/java/main'), file('build/resources/main'), module.artifactFile]
        assertHasImplementationClasspath(pluginMetadata, expectedClasspath)
    }

    def "can configure plugin and test source set by extension"() {
        given:
        buildFile << """
            sourceSets {
                custom {
                    java {
                        srcDir 'src'
                        compileClasspath = configurations.compileClasspath
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
                testClassesDirs = sourceSets.functionalTest.output.classesDirs
                classpath = sourceSets.functionalTest.runtimeClasspath
            }

            check.dependsOn functionalTest

            gradlePlugin {
                pluginSourceSet sourceSets.custom
                testSourceSets sourceSets.functionalTest
            }
        """
        def module = mavenRepo.module('org.gradle.test', 'a', '1.3').publish()
        buildFile << compileDependency('customImplementation', module)

        when:
        succeeds 'build'

        then:
        executedAndNotSkipped PLUGIN_UNDER_TEST_METADATA_TASK_PATH
        def pluginMetadata = file("build/$PLUGIN_UNDER_TEST_METADATA_TASK_NAME/$METADATA_FILE_NAME")
        def expectedClasspath = [file('build/classes/java/custom'), file('build/resources/custom'), module.artifactFile]
        assertHasImplementationClasspath(pluginMetadata, expectedClasspath)
    }

    def "configuration of test source sets by extension using the testSourceSet method is additive"() {
        given:
        buildFile << """
            sourceSets {
                integrationTest {
                    java {
                        srcDir 'src/integration/java'
                    }
                }

                functionalTest {
                    java {
                        srcDir 'src/functional/java'
                    }
                }
            }

            gradlePlugin {
                testSourceSet sourceSets.functionalTest
            }

            gradlePlugin {
                testSourceSet sourceSets.integrationTest
            }

            task assertFunctionalTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.functionalTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }

            task assertIntegrationTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.integrationTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }
        """

        expect:
        succeeds 'assertFunctionalTestHasTestKit', 'assertIntegrationTestHasTestKit'
    }

    def "configuration of test source sets by extension using testSourceSets method is NOT additive"() {
        given:
        buildFile << """
            sourceSets {
                integrationTest {
                    java {
                        srcDir 'src/integration/java'
                    }
                }

                functionalTest {
                    java {
                        srcDir 'src/functional/java'
                    }
                }
            }

            gradlePlugin {
                testSourceSets sourceSets.functionalTest
            }

            gradlePlugin {
                testSourceSets sourceSets.integrationTest
            }

            task assertFunctionalTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.functionalTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert !testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }

            task assertIntegrationTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.integrationTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }
        """

        expect:
        succeeds 'assertFunctionalTestHasTestKit', 'assertIntegrationTestHasTestKit'
    }

    def "usage of NON additive testSourceSets method overwrites earlier additive usage of testSourceSet"() {
        given:
        buildFile << """
            def integrationTest = sourceSets.create('integrationTest') {
                java {
                    srcDir 'src/integration/java'
                }
            }

            def functionalTest = sourceSets.create('functionalTest') {
                java {
                    srcDir 'src/functional/java'
                }
            }

            gradlePlugin {
                testSourceSet integrationTest
                testSourceSets functionalTest
            }

            task assertIntegrationTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.integrationTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert !testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }

            task assertFunctionalTestHasTestKit() {
                def testRuntimeClasspath = project.sourceSets.functionalTest.runtimeClasspath
                def testKit = dependencies.gradleTestKit().files
                doLast {
                    assert testRuntimeClasspath.files.containsAll(testKit.files)
                }
            }
        """

        expect:
        succeeds 'assertIntegrationTestHasTestKit', 'assertFunctionalTestHasTestKit'
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

    static void assertHasImplementationClasspath(File pluginMetadata, List<File> expected) {
        assert pluginMetadata.exists() && pluginMetadata.isFile()
        assert !pluginMetadata.text.contains("#")
        def implementationClasspath = GUtil.loadProperties(pluginMetadata).getProperty(IMPLEMENTATION_CLASSPATH_PROP_KEY)
        assert !implementationClasspath.contains("\\")
        def expectedEntries = normaliseFileAndLineSeparators(expected.collect { it.absolutePath }.join(File.pathSeparator))
        assert implementationClasspath == expectedEntries
    }
}
