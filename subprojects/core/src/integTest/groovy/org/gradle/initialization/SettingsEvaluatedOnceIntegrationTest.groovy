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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/38623")
class SettingsEvaluatedOnceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        file("init.gradle") << """
            beforeSettings { println("beforeSettings: " + it.settingsDir) }
            settingsEvaluated { println("settingsEvaluated: " + it.settingsDir) }
        """
        executer.withArgument("-I").withArgument(file("init.gradle").absolutePath)
    }

    def "settings lifecycle callbacks fire once for an ordinary build"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        succeeds("help")

        then:
        countOf("beforeSettings") == 1
        countOf("settingsEvaluated") == 1
    }

    def "settings lifecycle callbacks fire once when targeting a buildSrc directory with no settings script"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        file("buildSrc/build.gradle") << ""

        when:
        executer.withArgument("-p").withArgument(file("buildSrc").absolutePath)
        succeeds("help")

        then:
        countOf("beforeSettings") == 1
        countOf("settingsEvaluated") == 1
    }

    private int countOf(String prefix) {
        return result.output.readLines().count { it.startsWith(prefix + ":") }
    }
}
