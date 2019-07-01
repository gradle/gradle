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

    def setup() {
        executer.requireGradleDistribution() // need accurate classloading
    }

    private void doConfigurePlugin() {
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
        doConfigurePlugin()
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
    def "settings script with a plugins block with apply false - #settingScriptExtension"() {
        given:
        doConfigurePlugin()
        file("settings$settingScriptExtension") << use

        when:
        fails 'customTask'

        then:
        errorOutput.contains("Task 'customTask' not found in root project")

        where:
        settingScriptExtension | use
        '.gradle.kts'          | "plugins { id(\"$PLUGIN_ID\").version(\"$VERSION\").apply(false) }"
        '.gradle'              | "plugins { id '$PLUGIN_ID' version '$VERSION' apply false }"
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
}
