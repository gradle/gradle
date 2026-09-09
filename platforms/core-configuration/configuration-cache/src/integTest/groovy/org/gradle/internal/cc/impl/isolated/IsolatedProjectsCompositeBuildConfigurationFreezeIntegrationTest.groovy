/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39040")
@IntegrationTestTimeout(120)
class IsolatedProjectsCompositeBuildConfigurationFreezeIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements CompositeBuildFixture {

    def "configuring a plugin build with subprojects from the root project does not freeze with two workers using the #scheduler scheduler"() {
        given:
        includedBuild("plugins") {
            settingsScript << """
                include("sub")
            """
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }
        createDirs("plugins/sub", "a")

        includePluginBuild(settingsFile, "plugins")
        settingsFile << """
            include("a")
        """
        applyPlugins(buildFile, "plugin-a")

        expect:
        isolatedProjectsRun("help", "--max-workers=2", "-Dorg.gradle.internal.isolated-projects.scheduler=$scheduler")

        where:
        scheduler << ["jit", "aot"]
    }

    def "configuring nested plugin builds with subprojects does not freeze with two workers using the #scheduler scheduler"() {
        given:
        includedBuild("plugins-inner") {
            settingsScript << """
                include("sub")
            """
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-inner.gradle") << ""
        }
        includedBuild("plugins") {
            includePluginBuild(settingsScript, "../plugins-inner")
            settingsScript << """
                include("sub")
            """
            applyPlugins(buildScript, "groovy-gradle-plugin", "plugin-inner")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }
        createDirs("plugins-inner/sub", "plugins/sub", "a")

        includePluginBuild(settingsFile, "plugins")
        settingsFile << """
            include("a")
        """
        applyPlugins(buildFile, "plugin-a")

        expect:
        isolatedProjectsRun("help", "--max-workers=2", "-Dorg.gradle.internal.isolated-projects.scheduler=$scheduler")

        where:
        scheduler << ["jit", "aot"]
    }
}
