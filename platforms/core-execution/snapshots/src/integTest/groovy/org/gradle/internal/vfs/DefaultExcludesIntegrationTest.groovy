/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.vfs

import org.apache.tools.ant.DirectoryScanner
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class DefaultExcludesIntegrationTest extends AbstractIntegrationSpec {

    private static final EXCLUDED_FILE_NAME = "my-excluded-file.txt"

    def outputDir = file("build/output")
    def excludedFile = file("input/${EXCLUDED_FILE_NAME}")
    def copyOfExcludedFile = outputDir.file(EXCLUDED_FILE_NAME)

    def setup() {
        file("input/inputFile.txt").text = "input"
        excludedFile.text = "excluded"
        buildFile << """
            task copyTask(type: Copy) {
                from("input")
                into("build/output")
            }
        """
    }

    def "default excludes defined via settings.fileSystemDefaultExcludes are used"() {
        settingsFile << """
            fileSystemDefaultExcludes.add('**/${EXCLUDED_FILE_NAME}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists()

        when:
        excludedFile.text = "changed"
        run "copyTask"
        then:
        skipped(":copyTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/27225")
    def "settings.fileSystemDefaultExcludes can remove a built-in default exclude"() {
        def defaultExclude = '.gitignore'
        def defaultExcludeFile = file("input/$defaultExclude")
        defaultExcludeFile << "some content"

        settingsFile << """
            fileSystemDefaultExcludes.set(fileSystemDefaultExcludes.get() - '**/${defaultExclude}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        file("build/output/$defaultExclude").exists()

        when:
        defaultExcludeFile.text = "changed"
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
    }

    def "settings.fileSystemDefaultExcludes reverts to baseline when nothing is configured"() {
        settingsFile << """
            fileSystemDefaultExcludes.add('**/${EXCLUDED_FILE_NAME}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        !copyOfExcludedFile.exists()

        when:
        settingsFile.text = ""
        excludedFile.text = "changed"
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        copyOfExcludedFile.exists()
    }

    def "mutating Ant's DirectoryScanner from settings is silently ignored"() {
        // Gradle 10 removed the deprecated DirectoryScanner-mutation path. Calls to
        // org.apache.tools.ant.DirectoryScanner.add/removeDefaultExclude still compile and
        // mutate Ant's static state, but Gradle no longer consults it.
        settingsFile << """
            ${DirectoryScanner.name}.addDefaultExclude('**/${EXCLUDED_FILE_NAME}')
        """

        when:
        run "copyTask"
        then:
        executedAndNotSkipped(":copyTask")
        // Built-in defaults still apply (e.g. .git exclusions); the user's Ant mutation is ignored.
        copyOfExcludedFile.exists()
    }
}
