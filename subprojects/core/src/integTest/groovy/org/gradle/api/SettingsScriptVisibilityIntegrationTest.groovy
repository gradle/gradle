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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import spock.lang.IgnoreIf
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle-private/issues/3247")
@IgnoreIf({OperatingSystem.current().macOsX && JavaVersion.current() == JavaVersion.VERSION_1_8})
class SettingsScriptVisibilityIntegrationTest extends AbstractIntegrationSpec {

    @org.junit.Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def "classes loaded in settings are visible to buildSrc build scripts and project scripts"() {
        def plugin1Builder = new PluginBuilder(file("plugin1"))
        plugin1Builder.addSettingsPlugin("", "test.plugin1", "MySettingsPlugin1")
        def plugin1Jar = file("plugin1.jar")
        plugin1Builder.publishTo(executer, plugin1Jar)
        def plugin1ClassName = "${plugin1Builder.packageName}.MySettingsPlugin1"

        def plugin2Builder = new PluginBuilder(file("plugin2"))
        plugin2Builder.addSettingsPlugin("", "test.plugin2", "MySettingsPlugin2")
        def plugin2Jar = file("plugin2.jar")
        plugin2Builder.publishTo(executer, plugin2Jar)
        def plugin2ClassName = "${plugin2Builder.packageName}.MySettingsPlugin2"

        settingsFile << """
            buildscript { dependencies { classpath files(\"${plugin1Jar.name}\") } }

            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "settings: plugin 1 visible"
        """

        file("buildSrc/settings.gradle") << """
            buildscript { dependencies { classpath files(\"../${plugin2Jar.name}\") } }

            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "buildSrc settings: plugin 1 visible"
            getClass().classLoader.loadClass('${plugin2ClassName}')
            println "buildSrc settings: plugin 2 visible"
        """

        file("buildSrc/build.gradle") << """
            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "buildSrc: plugin 1 visible"
            getClass().classLoader.loadClass('${plugin2ClassName}')
            println "buildSrc: plugin 2 visible"
        """

        file("build.gradle") << """
            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "project: plugin 1 visible"
            try {
                getClass().classLoader.loadClass('${plugin2ClassName}')
            } catch (ClassNotFoundException e) {
                println "project: plugin 2 not visible"
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("settings: plugin 1 visible")
        outputContains("buildSrc settings: plugin 1 visible")
        outputContains("buildSrc settings: plugin 2 visible")
        outputContains("buildSrc: plugin 1 visible")
        outputContains("buildSrc: plugin 2 visible")
        outputContains("project: plugin 1 visible")
        outputContains("project: plugin 2 not visible")
    }

    def "classes loaded in settings are visible when -b is used"() {
        def plugin1Builder = new PluginBuilder(file("plugin1"))
        plugin1Builder.addSettingsPlugin("", "test.plugin1", "MySettingsPlugin1")
        def plugin1Jar = file("plugin1.jar")
        plugin1Builder.publishTo(executer, plugin1Jar)
        def plugin1ClassName = "${plugin1Builder.packageName}.MySettingsPlugin1"

        settingsFile << """
            buildscript { dependencies { classpath files(\"${plugin1Jar.name}\") } }

            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "settings: plugin 1 visible"
        """

        file("other-build.gradle") << """
            getClass().classLoader.loadClass('${plugin1ClassName}')
            println "project: plugin 1 visible"
        """

        when:
        executer.expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        succeeds("help", "-b", "other-build.gradle")

        then:
        outputContains("settings: plugin 1 visible")
        outputContains("project: plugin 1 visible")
    }

}
