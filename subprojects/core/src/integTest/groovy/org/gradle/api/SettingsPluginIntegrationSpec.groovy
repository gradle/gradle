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

class SettingsPluginIntegrationSpec extends AbstractIntegrationSpec {
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

    def "can apply plugin class from buildSrc"() {
        setup:
        file("buildSrc/src/main/java/test/SimpleSettingsPlugin.java").createFile().text = """
            package test;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class SimpleSettingsPlugin implements Plugin<Settings> {
                public void apply(Settings settings) {
                    settings.include(new String[]{"moduleA"});
                }
            }

            """

        when:
        settingsFile << "apply plugin: test.SimpleSettingsPlugin"

        then:
        succeeds(':moduleA:dependencies')
    }

    def "can apply script with relative path"() {
        setup:
        def settingsPluginScript = testDirectory.createDir("somePath").createFile("settingsPlugin.gradle")
        settingsPluginScript << "include 'moduleA'";

        when:
        settingsFile << "apply from: 'somePath/settingsPlugin.gradle'"

        then:
        succeeds(':moduleA:dependencies')
    }
}
