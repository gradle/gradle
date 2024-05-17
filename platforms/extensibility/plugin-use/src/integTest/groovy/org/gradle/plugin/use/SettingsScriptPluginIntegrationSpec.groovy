/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
class SettingsScriptPluginIntegrationSpec extends AbstractPluginSpec {

    private void doConfigureSettingsPlugin() {
        publishSettingPlugin("""
settings.gradle.beforeProject { org.gradle.api.Project project ->
    project.tasks.register("customTask") {
        doLast {
            System.out.println("Executing task added by a 'Settings' plugin")
        }
    }
}
"""
        )
    }

    def "settings script with a plugins block - #settingScriptExtension"() {
        given:
        doConfigureSettingsPlugin()
        file("settings$settingScriptExtension") << use

        when:
        succeeds 'customTask'

        then:
        outputContains("Executing task added by a 'Settings' plugin")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | USE_KOTLIN
        '.gradle'              | USE
    }

    def "multiple plugins blocks in settings fail the build - #settingScriptExtension"() {
        given:
        file("settings$settingScriptExtension") << use

        when:
        fails 'help'

        then:
        errorOutput.contains("plugins")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { } \n plugins { }"
    }

    def "plugins block before a plugins management block - #settingScriptExtension"() {
        given:
        file("settings$settingScriptExtension") << use

        when:
        fails 'help'

        then:
        errorOutput.contains("plugins")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { } \n pluginManagement { }"
        '.gradle'              | "plugins { } \n pluginManagement { }"
    }

    def "plugin with an unknown identifier in a plugins management block - #settingScriptExtension"() {
        given:
        file("settings$settingScriptExtension") << use

        when:
        fails 'help'

        then:
        errorOutput.contains("Plugin [id: 'unknown', version: '1.0'] was not found in any of the following sources:")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { id(\"unknown\") version \"1.0\" }"
        '.gradle'              | "plugins { id 'unknown' version '1.0' }"
    }

    def "can use apply false on settings - #settingScriptExtension"() {
        given:
        doConfigureSettingsPlugin()
        file("settings$settingScriptExtension") << use
        def initScript = file("init.gradle") << """
            settingsEvaluated {
                println it.buildscript.classLoader.loadClass("${pluginBuilder.packageName}.TestSettingsPlugin").name
            }
        """

        when:
        executer.usingInitScript(initScript)
        succeeds 'help'

        then:
        outputContains("${pluginBuilder.packageName}.TestSettingsPlugin")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { id(\"$PLUGIN_ID\").version(\"$VERSION\").apply(false) }"
        '.gradle'              | "plugins { id \"$PLUGIN_ID\" version \"$VERSION\" apply false }"
    }

    def "plugin management block can be used to configure the version of plugins used in settings - #settingScriptExtension"() {
        given:
        doConfigureSettingsPlugin()
        file("settings$settingScriptExtension") << use

        when:
        succeeds 'customTask'

        then:
        outputContains("Executing task added by a 'Settings' plugin")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "pluginManagement { $USE_KOTLIN  }\nplugins { id(\"$PLUGIN_ID\") }"
        '.gradle'              | "pluginManagement { $USE  }\nplugins { id '$PLUGIN_ID' }"
    }

    def "plugin management execution ordering - #settingScriptExtension"() {
        file("settings$settingScriptExtension") << """
pluginManagement {
    ${createPrintln("pluginManagement")};
    plugins { ${createPrintln("pluginManagement.plugins")} }
 }
 plugins { id(\"unknown\") }
 """

        when:
        fails 'help'

        then:
        errorOutput.contains("Plugin [id: 'unknown'] was not found in any of the following sources:")
        outputContains("In `pluginManagement`\nIn `pluginManagement.plugins`")

        where:
        settingScriptExtension | unused
        '.gradle.kts'          | false
        '.gradle'              | false
    }

    def "settings plugin can contribute to plugin management"() {
        when:
        pluginBuilder.addSettingsPlugin("settings.pluginManagement.plugins.id('com.test').version('1.0')", "com.test-settings")
        pluginBuilder.publishAs("g", "settings-plugin", "1.0", pluginRepo, executer).allowAll()
        pluginBuilder.addPlugin("project.tasks.create('pluginTask')", "com.test")
        pluginBuilder.publishAs("g", "project-plugin", "1.0", pluginRepo, executer).allowAll()

        file("settings.gradle") << """
pluginManagement {
    repositories {
        maven { url = "$pluginRepo.uri" }
    }
    plugins {
        id("com.test-settings") version "1.0"
    }
 }
 plugins { id("com.test-settings") }
 """

        buildFile << """
            plugins {
                id "com.test"
            }
        """

        then:
        succeeds("pluginTask")
    }

    String createPrintln(String location) {
        return "System.out.println(\"In `$location`\")"
    }
}
