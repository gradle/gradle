/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.test.fixtures.archive.JarTestFixture
import static org.gradle.play.integtest.fixtures.Repositories.*

class MixedPlayAndJvmLibraryProjectIntegrationTest extends AbstractIntegrationSpec {
    TestJvmComponent jvmApp = new TestJavaComponent()
    PlayApp playApp = new BasicPlayApp()

    def setup() {
        playApp.writeSources(testDirectory)
        jvmApp.writeSources(file("src/jvmLib"))
        jvmApp.writeResources(file("src/jvmLib/resources"))
        settingsFile.text = "rootProject.name = 'mixedJvmAndPlay'"
        buildFile.text = """
            plugins {
                id 'jvm-component'
                id '${jvmApp.languageName}-lang'
                id 'play'
            }

            ${PLAY_REPOSITORIES}

            model {
                components {
                    jvmLib(JvmLibrarySpec)
                }
            }
        """
    }

    def "assemble builds jvm component and play component binaries"() {
        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(
                ":createJvmLibJar",
                ":jvmLibJar",
                ":createPlayBinaryJar",
                ":playBinary",
                ":assemble")

        and:
        file("build/classes/jvmLib/jar").assertHasDescendants(jvmApp.expectedClasses*.fullPath as String[])
        file("build/resources/jvmLib/jar").assertHasDescendants(jvmApp.resources*.fullPath as String[])

        new JarTestFixture(file("build/jars/jvmLib/jar/jvmLib.jar")).hasDescendants(jvmApp.expectedOutputs*.fullPath as String[])
        file("build/playBinary/lib/mixedJvmAndPlay.jar").exists()
        file("build/playBinary/lib/mixedJvmAndPlay-assets.jar").exists()
    }

}
