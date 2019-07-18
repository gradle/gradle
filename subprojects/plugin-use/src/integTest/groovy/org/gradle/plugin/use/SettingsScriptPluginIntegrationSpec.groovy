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
import spock.lang.Unroll

@LeaksFileHandles
class SettingsScriptPluginIntegrationSpec extends AbstractPluginSpec {
    private static final String USE_APPLY_FALSE = "plugins { id '$PLUGIN_ID' version '$VERSION' apply false }"
    private static final String USE_APPLY_FALSE_KOTLIN = "plugins { id(\"$PLUGIN_ID\").version(\"$VERSION\").apply(false) }"

    def setup() {
        executer.requireGradleDistribution() // need accurate classloading
    }

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

    @Unroll
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

    @Unroll
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
        '.gradle'              | "plugins { } \n plugins { }"
    }

    @Unroll
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

    @Unroll
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

    @Unroll
    def "plugins block does not allow apply false on settings - #settingScriptExtension"() {
        given:
        file("settings$settingScriptExtension") << use

        when:
        fails 'help'

        then:
        errorOutput.contains("Use of 'apply false' on this script type is not supported.")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { id(\"noop\").version(\"1.0\").apply(false) }"
        '.gradle'              | "plugins { id 'noop' version '1.0' apply false }"
    }

    @Unroll
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

    @Unroll
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

    String createPrintln(String location) {
        return "System.out.println(\"In `$location`\")"
    }
}
