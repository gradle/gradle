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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class SettingsPluginIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup(){
        executer.usingSettingsFile(settingsFile)
        settingsFile << "rootProject.projectDir = file('..')\n"
    }

    @ToBeFixedForInstantExecution
    def "can apply plugin class from settings.gradle"() {
        when:
        settingsFile << """
        apply plugin: SimpleSettingsPlugin

        class SimpleSettingsPlugin implements Plugin<Settings> {
            void apply(Settings mySettings) {
                mySettings.include("moduleA");
            }
        }
        """

        then:
        succeeds(':moduleA:dependencies')
    }

    @ToBeFixedForInstantExecution
    def "can apply script with relative path"() {
        setup:
        testDirectory.createFile("settings/somePath/settingsPlugin.gradle") << "apply from: 'path2/settings.gradle'";
        testDirectory.createFile("settings/somePath/path2/settings.gradle") << "include 'moduleA'";

        when:
        settingsFile << "apply from: 'somePath/settingsPlugin.gradle'"

        then:
        succeeds(':moduleA:dependencies')
    }

    @ToBeFixedForInstantExecution
    def "can use plugins block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", pluginPortal, createExecuter()).allowAll()

        when:
        settingsFile.text = """
            plugins {
                id "test-settings-plugin" version "1.0"   
            }
            
            $settingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    @ToBeFixedForInstantExecution
    def "can use plugins block with plugin management block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", mavenHttpRepo, createExecuter()).allowAll()

        when:
        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url "$mavenHttpRepo.uri" }
                }
            }   
            plugins {
                id "test-settings-plugin" version "1.0"   
            }
            
            $settingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    protected TestFile getSettingsFile() {
        testDirectory.file('settings/settings.gradle')
    }
}
