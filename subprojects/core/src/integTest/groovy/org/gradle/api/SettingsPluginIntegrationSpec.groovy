/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle-private/issues/3247")
@Requires(UnitTestPreconditions.NotJava8OnMacOs)
class SettingsPluginIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup(){
        // Stop traversing to parent directory; otherwise embedded test execution will
        // find and load the `gradle.properties` file in the root of the source repository
        settingsFile.createFile()
        executer.inDirectory(file("settings"))
        relocatedSettingsFile << "rootProject.projectDir = file('..')\n"
    }

    def "can apply plugin class from settings.gradle"() {
        when:
        relocatedSettingsFile << """
        apply plugin: SimpleSettingsPlugin

        class SimpleSettingsPlugin implements Plugin<Settings> {
            void apply(Settings mySettings) {
                mySettings.include("moduleA");
            }
        }
        """

        then:
        succeeds(':moduleA:help')
    }

    def "can apply script with relative path"() {
        setup:
        testDirectory.createFile("settings/somePath/settingsPlugin.gradle") << "apply from: 'path2/settings.gradle'";
        testDirectory.createFile("settings/somePath/path2/settings.gradle") << "include 'moduleA'";

        when:
        relocatedSettingsFile << "apply from: 'somePath/settingsPlugin.gradle'"

        then:
        succeeds(':moduleA:help')
    }

    def "can use plugins block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", pluginPortal, createExecuter()).allowAll()

        when:
        relocatedSettingsFile.text = """
            plugins {
                id "test-settings-plugin" version "1.0"
            }

            $relocatedSettingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    def "can use plugins block with plugin management block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", mavenHttpRepo, createExecuter()).allowAll()

        when:
        relocatedSettingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url "$mavenHttpRepo.uri" }
                }
            }
            plugins {
                id "test-settings-plugin" version "1.0"
            }

            $relocatedSettingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    protected TestFile getRelocatedSettingsFile() {
        testDirectory.file('settings/settings.gradle')
    }
}
