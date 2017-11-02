/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock.generation

import org.gradle.test.fixtures.plugin.PluginBuilder

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.mavenRepository

class DependencyLockBuildScriptDependenciesIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    private static final String BUILD_ENVIRONMENT_TASK_NAME = 'buildEnvironment'

    def "does not write lock file for resolvable buildscript dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << """
            buildscript {
                ${mavenRepository(mavenRepo)}

                dependencies {
                    classpath 'foo:bar:1.5'
                }
            }
        """

        when:
        succeedsWithEnabledDependencyLocking(BUILD_ENVIRONMENT_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not write lock file for resolvable plugin declared through plugins DSL"() {
        given:
        def builder = new PluginBuilder(testDirectory.file('plugin'))
        builder.addPlugin('')
        builder.publishAs('com.gradle:test-plugin:1.0', mavenRepo, executer)

        buildFile << """
            plugins {
                id 'test-plugin' version '1.0'
            }
        """
        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
        """

        when:
        succeedsWithEnabledDependencyLocking(BUILD_ENVIRONMENT_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }
}
