/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.configuration.ApplyScriptPluginBuildOperationDetails
import org.gradle.configuration.project.ConfigureProjectBuildOperationDetails
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.junit.Rule

class ApplyPluginBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BuildOperationsFixture operations = new BuildOperationsFixture(executer, temporaryFolder)

    def 'emits events for applications'() {
        given:
        file("a/build.gradle") << "apply plugin: 'java'"
        file("b/build.gradle") << "apply plugin: 'groovy'"
        settingsFile << """
            include 'a'
            include 'b'
        """

        when:
        succeeds "build"

        then:
        def a = operations.operation(ConfigureProjectBuildOperationDetails) {
            it.details.projectPath.path == ":a"
        }
        def aPlugins = a.children {
            it.detailsType == ApplyPluginBuildOperationDetails
        }
        aPlugins.details*.className == [
            "org.gradle.api.plugins.HelpTasksPlugin",
            "org.gradle.api.plugins.JavaPlugin",
            "org.gradle.api.plugins.JavaBasePlugin",
            "org.gradle.api.plugins.BasePlugin",
            "org.gradle.api.plugins.ReportingBasePlugin",
            "org.gradle.language.base.plugins.LanguageBasePlugin",
            "org.gradle.platform.base.plugins.BinaryBasePlugin",
            "org.gradle.language.base.plugins.LifecycleBasePlugin",
            "org.gradle.platform.base.plugins.ComponentBasePlugin"
        ]
        aPlugins.details*.pluginId*.id == [
            "org.gradle.help-tasks",
            "org.gradle.java",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        ]

        and:
        def b = operations.operation(ConfigureProjectBuildOperationDetails) {
            it.details.projectPath.path == ":b"
        }
        def bPlugins = b.children {
            it.detailsType == ApplyPluginBuildOperationDetails
        }
        bPlugins.details*.className == [
            "org.gradle.api.plugins.HelpTasksPlugin",
            "org.gradle.api.plugins.GroovyPlugin",
            "org.gradle.api.plugins.GroovyBasePlugin",
            "org.gradle.api.plugins.JavaPlugin",
            "org.gradle.api.plugins.JavaBasePlugin",
            "org.gradle.api.plugins.BasePlugin",
            "org.gradle.api.plugins.ReportingBasePlugin",
            "org.gradle.language.base.plugins.LanguageBasePlugin",
            "org.gradle.platform.base.plugins.BinaryBasePlugin",
            "org.gradle.language.base.plugins.LifecycleBasePlugin",
            "org.gradle.platform.base.plugins.ComponentBasePlugin"
        ]
        bPlugins.details*.pluginId*.id == [
            "org.gradle.help-tasks",
            "org.gradle.groovy",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        ]
    }

    def "location of application can be ascertained from parent operation"() {
        given:
        buildScript """
            apply from: "foo.gradle"
        """
        file("foo.gradle") << """
            apply plugin: "groovy"
        """

        when:
        succeeds("build")

        then:
        def applyFoo = operations.operation(ApplyScriptPluginBuildOperationDetails) {
            it.details.file.absolutePath == file("foo.gradle").absolutePath
        }

        def fooPlugins = applyFoo.children {
            it.detailsType == ApplyPluginBuildOperationDetails
        }

        fooPlugins.details.pluginId.id == ["org.gradle.groovy"]
    }
}
