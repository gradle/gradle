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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.plugins.internal.tasks.PluginUnderTestMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification

class JavaGradlePluginPluginTestKitSetupTest extends Specification {

    Project project = TestUtil.builder().build()

    def setup() {
        project.pluginManager.apply(JavaGradlePluginPlugin)
        Configuration configuration = project.configurations.create('gradleApi')
        project.dependencies.add(configuration.name, project.dependencies.gradleApi())
    }

    def "can configure functional testing by conventions"() {
        when:
        project.evaluate()

        then:
        PluginUnderTestMetadata pluginUnderTestMetadataTask = project.tasks.getByPath(JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME)
        assertTaskPluginClasspath(pluginUnderTestMetadataTask, project.sourceSets.main)
        assertTestKitDependency(project, project.sourceSets.test)
        assertInferredTaskDependency(pluginUnderTestMetadataTask, project.sourceSets.test)
        assertTestTaskDependency(pluginUnderTestMetadataTask, project.tasks.getByPath('test'))
    }

    def "can configure plugin and test source set by extension"() {
        when:
        project.sourceSets {
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

        project.tasks.create('functionalTest', Test) {
            testClassesDir = project.sourceSets.functionalTest.output.classesDir
            classpath = project.sourceSets.functionalTest.runtimeClasspath
        }

        project.gradlePlugin {
            pluginSourceSet project.sourceSets.customMain
            testSourceSets project.sourceSets.functionalTest
        }

        project.evaluate()

        then:
        PluginUnderTestMetadata pluginUnderTestMetadataTask = project.tasks.getByPath(JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME)
        assertTaskPluginClasspath(pluginUnderTestMetadataTask, project.sourceSets.customMain)
        assertTestKitDependency(project, project.sourceSets.functionalTest)
        assertInferredTaskDependency(pluginUnderTestMetadataTask, project.sourceSets.functionalTest)
        assertTestTaskDependency(pluginUnderTestMetadataTask, project.tasks.getByPath('functionalTest'))
    }

    def "can configure multiple test source sets"() {
        when:
        project.sourceSets {
            functionalTest1 {
                java {
                    srcDir 'src/functional1/java'
                }
                resources {
                    srcDir 'src/functional1/resources'
                }
            }

            functionalTest2 {
                java {
                    srcDir 'src/functional2/java'
                }
                resources {
                    srcDir 'src/functional2/resources'
                }
            }
        }

        project.tasks.create('functionalTest1', Test) {
            testClassesDir = project.sourceSets.functionalTest1.output.classesDir
            classpath = project.sourceSets.functionalTest1.runtimeClasspath
        }

        project.tasks.create('functionalTest2', Test) {
            testClassesDir = project.sourceSets.functionalTest2.output.classesDir
            classpath = project.sourceSets.functionalTest2.runtimeClasspath
        }

        project.gradlePlugin {
            testSourceSets project.sourceSets.functionalTest1, project.sourceSets.functionalTest2
        }

        project.evaluate()

        then:
        PluginUnderTestMetadata pluginUnderTestMetadataTask = project.tasks.getByPath(JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME)
        assertTaskPluginClasspath(pluginUnderTestMetadataTask, project.sourceSets.main)
        assertTestKitDependency(project, project.sourceSets.functionalTest1)
        assertTestKitDependency(project, project.sourceSets.functionalTest2)
        assertInferredTaskDependency(pluginUnderTestMetadataTask, project.sourceSets.functionalTest1)
        assertInferredTaskDependency(pluginUnderTestMetadataTask, project.sourceSets.functionalTest2)
        assertTestTaskDependency(pluginUnderTestMetadataTask, project.tasks.getByPath('functionalTest1'))
        assertTestTaskDependency(pluginUnderTestMetadataTask, project.tasks.getByPath('functionalTest2'))
    }

    private void assertTaskPluginClasspath(PluginUnderTestMetadata pluginClasspathManifestTask, SourceSet mainSourceSet) {
        assert pluginClasspathManifestTask.pluginClasspath.files == (mainSourceSet.runtimeClasspath - project.configurations.gradleApi.incoming.files).files
    }

    private void assertTestKitDependency(Project project, SourceSet testSourceSet) {
        assert project.configurations
                .getByName(testSourceSet.compileConfigurationName)
                .dependencies.find {
            it.source.files == project.dependencies.gradleTestKit().source.files
        }
    }

    private void assertInferredTaskDependency(PluginUnderTestMetadata pluginClasspathManifestTask, SourceSet testSourceSet) {
        project.configurations
                .getByName(testSourceSet.runtimeConfigurationName)
                .dependencies.find {
            it.source.files.containsAll(pluginClasspathManifestTask.outputs.files.files)
        }
    }

    private void assertTestTaskDependency(PluginUnderTestMetadata pluginUnderTestMetadataTask, Test testTask) {
        assert testTask.taskDependencies.getDependencies(testTask).contains(pluginUnderTestMetadataTask)
    }
}
