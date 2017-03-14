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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.PLUGIN_UNDER_TEST_METADATA_TASK_NAME
import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.TestKitAndPluginClasspathDependenciesAction

class JavaGradlePluginPluginTestKitSetupTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.plugins.apply(JavaPlugin)
    }

    def "can configure with default conventions"() {
        given:
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSet pluginSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet testSourceSet = javaConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        GradlePluginDevelopmentExtension extension = new GradlePluginDevelopmentExtension(project, pluginSourceSet, testSourceSet)
        PluginUnderTestMetadata pluginUnderTestMetadata = project.tasks.create(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata)
        Action<Project> action = new TestKitAndPluginClasspathDependenciesAction(extension, pluginUnderTestMetadata)

        when:
        action.execute(project)

        then:
        assertTestKitDependency(project, testSourceSet)
        assertInferredTaskDependency(pluginUnderTestMetadata, project.sourceSets.test)
        assertTestTaskDependency(pluginUnderTestMetadata, project.tasks.getByPath('test'))
    }

    def "can configure single custom main and test source set"() {
        given:
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

        GradlePluginDevelopmentExtension extension = new GradlePluginDevelopmentExtension(project, project.sourceSets.customMain, project.sourceSets.functionalTest)
        PluginUnderTestMetadata pluginUnderTestMetadata = project.tasks.create(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata)
        Action<Project> action = new TestKitAndPluginClasspathDependenciesAction(extension, pluginUnderTestMetadata)

        when:
        action.execute(project)

        then:
        assertTestKitDependency(project, project.sourceSets.functionalTest)
        assertInferredTaskDependency(pluginUnderTestMetadata, project.sourceSets.functionalTest)
        assertTestTaskDependency(pluginUnderTestMetadata, project.tasks.getByPath('functionalTest'))
    }

    def "can configure multiple custom test source sets"() {
        given:
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

        GradlePluginDevelopmentExtension extension = new GradlePluginDevelopmentExtension(project, project.sourceSets.main, project.sourceSets.functionalTest1, project.sourceSets.functionalTest2)
        PluginUnderTestMetadata pluginUnderTestMetadata = project.tasks.create(PLUGIN_UNDER_TEST_METADATA_TASK_NAME, PluginUnderTestMetadata)
        Action<Project> action = new TestKitAndPluginClasspathDependenciesAction(extension, pluginUnderTestMetadata)

        when:
        action.execute(project)

        then:
        assertTestKitDependency(project, project.sourceSets.functionalTest1)
        assertTestKitDependency(project, project.sourceSets.functionalTest2)
        assertInferredTaskDependency(pluginUnderTestMetadata, project.sourceSets.functionalTest1)
        assertInferredTaskDependency(pluginUnderTestMetadata, project.sourceSets.functionalTest2)
        assertTestTaskDependency(pluginUnderTestMetadata, project.tasks.getByPath('functionalTest1'))
        assertTestTaskDependency(pluginUnderTestMetadata, project.tasks.getByPath('functionalTest2'))
    }

    private void assertTestKitDependency(Project project, SourceSet testSourceSet) {
        assert project.configurations
                .getByName(testSourceSet.compileConfigurationName)
                .dependencies.find {
            it.files == project.dependencies.gradleTestKit().files
        }
    }

    private void assertInferredTaskDependency(PluginUnderTestMetadata pluginClasspathManifestTask, SourceSet testSourceSet) {
        project.configurations
                .getByName(testSourceSet.runtimeConfigurationName)
                .dependencies.find {
            it.files.containsAll(pluginClasspathManifestTask.outputs.files)
        }
    }

    private void assertTestTaskDependency(PluginUnderTestMetadata pluginUnderTestMetadataTask, Test testTask) {
        assert testTask.taskDependencies.getDependencies(testTask).contains(pluginUnderTestMetadataTask)
    }
}
