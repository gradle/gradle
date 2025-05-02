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

package org.gradle.internal.cc.impl.isolated

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

@SelfType(AbstractIntegrationSpec)
trait CompositeBuildFixture {

    def includedBuild(String root, @DelegatesTo(BuildLayout) Closure configure) {
        configure.setDelegate(
            new BuildLayout(
                file("$root/settings.gradle"),
                file("$root/build.gradle"),
                file("$root/src/main/groovy"))
        )
        configure()
    }

    def includeLibraryBuild(TestFile settingsFile, String build) {
        settingsFile << """
            includeBuild("$build")
        """
    }

    def applyPlugins(TestFile buildFile, String... pluginIds) {
        buildFile << """
            plugins {
                ${pluginIds.collect { """id "$it" """ }.join("\n")}
            }
        """
    }

    def includePluginBuild(TestFile settingsFile, String... builds) {
        settingsFile << """
            pluginManagement {
                ${builds.collect { """includeBuild("$it")""" }.join("\n")}
            }
        """
    }

    static class BuildLayout {
        TestFile settingsScript
        TestFile buildScript
        TestFile srcMainGroovy

        BuildLayout(TestFile settingsScript, TestFile buildScript, TestFile srcMainGroovy) {
            this.settingsScript = settingsScript
            this.buildScript = buildScript
            this.srcMainGroovy = srcMainGroovy
        }
    }
}
