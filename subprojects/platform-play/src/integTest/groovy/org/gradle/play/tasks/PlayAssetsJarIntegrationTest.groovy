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

package org.gradle.play.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK8_OR_LATER)
class PlayAssetsJarIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        new BasicPlayApp().writeSources(file("."))
        settingsFile << """ rootProject.name = 'play-app' """

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":createPlayBinaryJar", ":createPlayBinaryAssetsJar"
        jar("build/playBinary/lib/play-app-assets.jar").containsDescendants(
                "public/images/favicon.svg",
                "public/stylesheets/main.css",
                "public/javascripts/hello.js")
    }

    def "does not rebuild when public assets remain unchanged" () {
        when:
        succeeds "assemble"

        then:
        skipped ":createPlayBinaryJar", ":createPlayBinaryAssetsJar"
    }

    def "rebuilds when public assets change" () {
        when:
        file("public/stylesheets/main.css") << "\n"
        succeeds "assemble"

        then:
        executedAndNotSkipped ":createPlayBinaryAssetsJar"
        skipped ":createPlayBinaryJar"

        and:
        jar("build/playBinary/lib/play-app-assets.jar").assertFileContent("public/stylesheets/main.css", file("public/stylesheets/main.css").text)
    }

    def "rebuilds when public assets are removed" () {
        when:
        file("public/stylesheets/main.css").delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped ":createPlayBinaryAssetsJar"
        skipped ":createPlayBinaryJar"

        and:
        jar("build/playBinary/lib/play-app-assets.jar").countFiles("public/stylesheets/main.css") == 0
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
