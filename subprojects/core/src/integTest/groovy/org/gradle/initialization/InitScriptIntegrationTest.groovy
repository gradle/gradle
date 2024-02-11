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

package org.gradle.initialization


import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Issue

@LeaksFileHandles
class InitScriptIntegrationTest extends AbstractIntegrationSpec {

    private void createProject() {
        buildScript """
            task hello() {
                doLast {
                    println "Hello from main project"
                }
            }
        """

        file("buildSrc/build.gradle") << """
            task helloFromBuildSrc {
                doLast {
                    println "Hello from buildSrc"
                }
            }

            build.dependsOn(helloFromBuildSrc)
        """
    }

    @Issue(['GRADLE-1457', 'GRADLE-3197'])
    def 'init scripts passed on the command line are applied to buildSrc'() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        createProject()
        file("init.gradle") << initScript()

        executer.usingInitScript(file('init.gradle'))

        when:
        succeeds 'hello'

        then:
        output.contains("Project buildSrc evaluated")
        output.contains("Project hello evaluated")
    }

    def 'init scripts passed in the Gradle user home are applied to buildSrc'() {
        given:
        settingsFile << "rootProject.name = 'hello'"
        createProject()
        executer.requireOwnGradleUserHomeDir()
        new TestFile(executer.gradleUserHomeDir, "init.gradle") << initScript()

        when:
        succeeds 'hello'

        then:
        output.contains("Project buildSrc evaluated")
        output.contains("Project hello evaluated")
    }

    def 'init script can contribute to settings - before and after'() {
        given:
        createDirs("sub1", "sub2")
        file("init.gradle") << """
            beforeSettings {
                it.ext.addedInInit = ["beforeSettings"]
                it.include "sub1"
            }
            settingsEvaluated {
                it.ext.addedInInit << "settingsEvaluated"
                println "order: " + it.ext.addedInInit.join(" - ")
            }
        """

        file("settings.gradle") << """
            ext.addedInInit += "settings.gradle"
            include "sub2"
        """

        executer.usingInitScript(file('init.gradle'))

        buildFile << """
            task info {
                doLast {
                    println "subprojects: " + subprojects.path.join(" - ")
                }
            }
        """
        when:
        succeeds 'info'

        then:
        output.contains("order: beforeSettings - settings.gradle - settingsEvaluated")
        output.contains("subprojects: :sub1 - :sub2")
    }

    def "can apply settings plugin from init script"() {
        given:
        createDirs("sub1", "sub2")
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addPluginSource("settings-test", "test.SettingsPlugin", """
            package test

            class SettingsPlugin implements $Plugin.name<$Settings.name> {
                void apply($Settings.name settings) {
                    settings.ext.addedInPlugin = ["plugin"]
                    settings.include "sub1"
                }
            }
        """)
        def pluginJar = file("plugin.jar")
        pluginBuilder.publishTo(executer, pluginJar)

        file("init.gradle") << """
            initscript {
                dependencies {
                    classpath files("${pluginJar.name}")
                }
            }
            beforeSettings {
                it.plugins.apply(test.SettingsPlugin)
            }
            settingsEvaluated {
                it.ext.addedInPlugin << "settingsEvaluated"
                println "order: " + it.ext.addedInPlugin.join(" - ")
            }
        """

        file("settings.gradle") << """
            ext.addedInPlugin += "settings.gradle"
            include "sub2"
        """

        executer.usingInitScript(file('init.gradle'))

        buildFile << """
            task info {
                doLast {
                    println "subprojects: " + subprojects.path.join(" - ")
                }
            }
        """
        when:
        succeeds 'info'

        then:
        output.contains("order: plugin - settings.gradle - settingsEvaluated")
        output.contains("subprojects: :sub1 - :sub2")
    }

    private static String initScript() {
        """
            gradle.afterProject { p ->
                println "Project \${p.name} evaluated"
            }
        """
    }
}
