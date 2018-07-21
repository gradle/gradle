/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture

import static org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.play.integtest.fixtures.AbstractMultiVersionPlayReloadIntegrationTest.*

abstract class PlayMultiVersionApplicationIntegrationTest extends PlayMultiVersionIntegrationTest {
    abstract PlayApp getPlayApp()

    def setup() {
        playApp.writeSources(testDirectory)
        buildFile << playPlatformConfiguration(version.toString())
        buildFile << playLogbackDependenciesIfPlay25(versionNumber)
        settingsFile << """
            rootProject.name = '${playApp.name}'
        """
        executer.beforeExecute {
            executer.withArgument("-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}")
        }
    }

    static String playPlatformConfiguration(String version) {
        return """
        allprojects {
            model {
                components {
                    play {
                        targetPlatform "play-${version}"
                    }
                }
            }
        }
        """
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    ZipTestFixture zip(String fileName) {
        new ZipTestFixture(file(fileName))
    }

    TarTestFixture tar(String fileName) {
        new TarTestFixture(file(fileName))
    }
}
