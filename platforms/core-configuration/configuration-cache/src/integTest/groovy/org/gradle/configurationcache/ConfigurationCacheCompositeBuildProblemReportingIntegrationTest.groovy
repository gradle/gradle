/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.Plugin
import org.gradle.api.Project

class ConfigurationCacheCompositeBuildProblemReportingIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "reports task problems in tasks from included build that run in main build"() {
        given:
        settingsFile << """
            includeBuild 'inc'
        """
        file("inc/settings.gradle") << """
            include 'sub'
        """
        file("inc/sub/build.gradle") << """
            gradle.buildFinished { }
            tasks.register('broken') {
                inputs.property('p', project).optional(true)
                doLast { t -> t.project }
            }
        """

        when:
        configurationCacheFails ":inc:sub:broken"

        then:
        outputContains "Configuration cache entry discarded with 3 problems."
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'inc/sub/build.gradle': line 2: registration of listener on 'Gradle.buildFinished' is unsupported".replace('/', File.separator))
            withProblem("Build file 'inc/sub/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.".replace('/', File.separator))
            withProblem("Task `:inc:sub:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 2
        }
    }

    def "does not report problems in tasks that produce plugins"() {
        given:
        settingsFile << """
            includeBuild 'inc'
        """
        file("inc/settings.gradle") << """
            include 'sub'
        """
        file("inc/sub/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    p {
                        id = "test.plugin"
                        implementationClass = "test.PluginImpl"
                    }
                }
            }
            // Should not be reported
            gradle.buildFinished { }
            classes {
                inputs.property('p', project).optional(true)
                doLast { t -> t.project }
            }
            // Should be reported
            tasks.register('broken') {
                inputs.property('p', project).optional(true)
                doLast { t -> t.project }
            }
            tasks.register('ok') {
                doLast { }
            }
        """
        file("inc/sub/src/main/java/test/PluginImpl.java") << """
            package test;

            import ${Project.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Project> {
                public void apply(Project project) {
                }
            }
        """
        file("build.gradle") << """
            plugins {
                id("test.plugin")
            }
            tasks.register('ok') {
                doLast { }
            }
        """

        when:
        configurationCacheFails "ok"

        then:
        // TODO - should not fail
        outputContains "Configuration cache entry discarded with 1 problem."
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'inc/sub/build.gradle': line 14: registration of listener on 'Gradle.buildFinished' is unsupported".replace('/', File.separator))
        }
    }
}
