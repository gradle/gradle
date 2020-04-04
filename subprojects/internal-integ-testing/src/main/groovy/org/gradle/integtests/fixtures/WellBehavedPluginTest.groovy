/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.util.GUtil
import org.junit.Assume

import java.util.regex.Pattern

abstract class WellBehavedPluginTest extends AbstractPluginIntegrationTest {

    String getPluginName() {
        def matcher = Pattern.compile("(\\w+)Plugin(GoodBehaviour)?(Integ(ration)?)?Test").matcher(getClass().simpleName)
        if (matcher.matches()) {
            return GUtil.toWords(matcher.group(1), (char) '-')
        }
        throw new UnsupportedOperationException("Cannot determine plugin id from class name '${getClass().simpleName}.")
    }

    String getQualifiedPluginId() {
        DefaultPluginManager.CORE_PLUGIN_PREFIX + getPluginName()
    }

    String getMainTask() {
        return "assemble"
    }

    @ToBeFixedForInstantExecution(bottomSpecs = [
        "HelpTasksPluginIntegrationTest",
        "BuildDashboardPluginIntegrationTest",
        "GroovyPluginGoodBehaviourTest",
        "ScalaPluginGoodBehaviourTest",
        "AntlrPluginIntegrationTest",
        "PlayApplicationPluginGoodBehaviourIntegrationTest",
        "JavaGradlePluginPluginIntegrationTest",
        "ApplicationPluginIntegrationTest",
        "CheckstylePluginIntegrationTest",
        "CodeNarcPluginIntegrationTest",
        "PmdPluginIntegrationTest",
        "CppLibraryPluginIntegrationTest",
        "CppApplicationPluginIntegrationTest",
        "SwiftApplicationPluginIntegrationTest",
        "XcodePluginIntegrationTest",
        "IdeaPluginGoodBehaviourTest"
    ])
    def "can apply plugin unqualified"() {
        given:
        applyPluginUnqualified()

        expect:
        succeeds mainTask
    }

    @ToBeFixedForInstantExecution
    def "plugin does not force creation of build dir during configuration"() {
        given:
        applyPlugin()

        when:
        run "tasks"

        then:
        !file("build").exists()
    }

    @ToBeFixedForInstantExecution(bottomSpecs = [
        "HelpTasksPluginIntegrationTest",
        "BuildDashboardPluginIntegrationTest",
        "JavaGradlePluginPluginIntegrationTest",
        "GroovyPluginGoodBehaviourTest",
        "ScalaPluginGoodBehaviourTest",
        "AntlrPluginIntegrationTest",
        "PlayApplicationPluginGoodBehaviourIntegrationTest",
        "ApplicationPluginIntegrationTest",
        "CheckstylePluginIntegrationTest",
        "CodeNarcPluginIntegrationTest",
        "PmdPluginIntegrationTest",
        "CppLibraryPluginIntegrationTest",
        "CppApplicationPluginIntegrationTest",
        "SwiftApplicationPluginIntegrationTest",
        "XcodePluginIntegrationTest",
        "IdeaPluginGoodBehaviourTest"
    ])
    def "plugin can build with empty project"() {
        given:
        applyPlugin()

        expect:
        succeeds mainTask
    }

    protected applyPlugin(File target = buildFile) {
        target << "apply plugin: '${getQualifiedPluginId()}'\n"
    }

    protected applyPluginUnqualified(File target = buildFile) {
        target << "apply plugin: '${getPluginName()}'\n"
    }

    def "does not realize all possible tasks"() {
        // TODO: This isn't done yet, we still realize many tasks
        // Eventually, this should only realize "help"

        Assume.assumeFalse(pluginName in [
            'xctest', // Almost, still realizes compileTestSwift

            'visual-studio',
            'xcode',

            'play-application',
        ])

        applyPlugin()

        buildFile << """
            def configuredTasks = []
            tasks.configureEach {
                configuredTasks << it
            }

            gradle.buildFinished {
                def configuredTaskPaths = configuredTasks*.path

                assert configuredTaskPaths == [':help']
            }
        """
        expect:
        succeeds("help")
    }
}
