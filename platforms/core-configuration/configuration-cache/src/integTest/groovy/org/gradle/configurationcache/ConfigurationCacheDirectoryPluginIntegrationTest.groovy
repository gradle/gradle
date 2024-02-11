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

package org.gradle.configurationcache

class ConfigurationCacheDirectoryPluginIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "directory based plugins are instrumented and violations are reported"() {
        generateExternalPlugin("first", "FirstPlugin", "first.property")
        generateExternalPlugin("second", "SecondPlugin", "second.property")
        buildFile.text = """
            buildscript {
                dependencies {
                    classpath(files("./first/build/classes/java/main"))
                    classpath(files("./second/build/classes/java/main"))
                }
            }
            apply plugin: FirstPlugin
            apply plugin: SecondPlugin
        """

        when:
        executer.inDirectory(file("first")).withTasks("classes").run()
        executer.inDirectory(file("second")).withTasks("classes").run()
        configurationCacheRun("-Dfirst.property=first.value", "-Dsecond.property=second.value")

        then:
        outputContains("returned = first.value")
        outputContains("returned = second.value")
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
        problems.assertResultHasProblems(result) {
            withInput("Plugin class 'FirstPlugin': system property 'first.property'")
            withInput("Plugin class 'SecondPlugin': system property 'second.property'")
            ignoringUnexpectedInputs()
        }
    }

    private void generateExternalPlugin(String folder, String pluginName, String propertyName) {
        file("$folder/src/main/java/${pluginName}.java") << """
            import org.gradle.api.*;

            public class $pluginName implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    String returned = System.getProperty("$propertyName");
                    System.out.println("returned = " + returned);
                }
            }
        """
        file("$folder/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
        """
        file("$folder/settings.gradle") << "rootProject.name = '$folder'"
    }
}
