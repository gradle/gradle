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

class DefaultExcludesIntegrationTest extends AbstractIntegrationSpec{

    private static final EXCLUDED_FILE_NAME = "my-excluded-file.txt"

    def "default excludes defined in settings.gradle are used"() {
        buildFile << """
            task copyTask(type: Copy) {
                from("input")
                into("build/output")
            }
        """
        settingsFile << """
            ${DirectoryScanner.name}.addDefaultExclude('**/${EXCLUDED_FILE_NAME}')
        """

        def outputDir = file("build/output")
        file("input/inputFile.txt").text = "input"
        def excludedFile = file("input/${EXCLUDED_FILE_NAME}")
        excludedFile.text = "initial"
        def copyOfExcludedFile = outputDir.file(EXCLUDED_FILE_NAME)

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
}
