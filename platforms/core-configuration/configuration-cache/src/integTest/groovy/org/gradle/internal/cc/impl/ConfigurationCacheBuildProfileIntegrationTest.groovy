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

package org.gradle.internal.cc.impl

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ConfigurationCacheBuildProfileIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/18386")
    def "can profile a build with cc enabled"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        file("build.gradle") << """
            plugins {
               id("java")
            }
        """
        file("src/main/java/included/Example.java") << ""

        when:
        configurationCacheRun(":help", "--profile")

        then:
        configurationCache.assertStateStored()
        def report = findReport()

        and:
        report.delete()

        when:
        configurationCacheRun(":help", "--profile")

        then:
        configurationCache.assertStateLoaded()
        findReport()
    }

    @Issue("https://github.com/gradle/gradle/issues/18386")
    def "can profile a composite build with cc enabled"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        settingsFile << """
            pluginManagement {
                includeBuild 'build-logic'
            }
        """
        file("build-logic/src/main/java/FooPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class FooPlugin implements Plugin<Project> {
                public void apply(Project project) { }
            }
        """
        file("build-logic/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }

            gradlePlugin {
                plugins {
                    foo {
                        id = "foo.plugin"
                        implementationClass = "FooPlugin"
                    }
                }
            }
        """
        buildFile << """
            plugins{
                id('foo.plugin')
            }
        """

        when:
        configurationCacheRun(":help", "--profile")

        then:
        configurationCache.assertStateStored()
        def report = findReport()

        and:
        report.delete()

        when:
        configurationCacheRun(":help", "--profile")

        then:
        configurationCache.assertStateLoaded()
        findReport()
    }

    private TestFile findReport() {
        def reportFile = file('build/reports/profile').listFiles().find { it.name ==~ /profile-.+.html/ }
        assert reportFile && reportFile.exists()
        reportFile
    }
}
