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
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.Unroll

@LeaksFileHandles
class SettingsScriptPluginIntegrationSpec extends AbstractPluginSpec {

    def setup() {
        executer.requireGradleDistribution() // need accurate classloading
    }

    @Unroll
    def "settings script with plugin block"() {
        given:
        publishSettingPlugin("System.out.println(\"Executing a 'Settings' plugin\")")
        file("settings$settingSscriptExtension") << use

        when:
        succeeds 'help'

        then:
        outputContains("Executing a 'Settings' plugin")

        where:
        settingSscriptExtension | use
        '.gradle'               | USE
        '.gradle.kts'           | USE_KOTLIN
    }
}
