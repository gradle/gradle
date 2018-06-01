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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture

abstract class PlayMultiVersionApplicationIntegrationTest extends PlayMultiVersionIntegrationTest {
    abstract PlayApp getPlayApp()

    def setup() {
        playApp.writeSources(testDirectory)
        buildFile << """
        allprojects {
            model {
                components {
                    play {
                        targetPlatform "play-${MultiVersionIntegrationSpec.version}"
                    }
                }
            }
        }
        """
        settingsFile << """
            rootProject.name = '${playApp.name}'
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

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        // trait Controller in package mvc is deprecated (since 2.6.0)
        // application - Logger configuration in conf files is deprecated and has no effect (since 2.4.0)
        executer.noDeprecationChecks()
        return super.succeeds(tasks)
    }
}
