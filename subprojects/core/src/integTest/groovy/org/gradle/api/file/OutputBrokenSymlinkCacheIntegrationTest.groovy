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
package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

@Requires(TestPrecondition.SYMLINKS)
class OutputBrokenSymlinkCacheIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/9906")
    def "don't cache if task produces broken symlink"() {
        buildFile << """
            import java.nio.file.*
            class ProducesLink extends DefaultTask {
                @OutputDirectory File outputDirectory

                @TaskAction execute() {
                    Files.createSymbolicLink(Paths.get('root/link'), Paths.get('target'));
                }
            }

            task producesLink(type: ProducesLink) {
                outputDirectory = file 'root'
                outputs.cacheIf { true }
            }
        """

        when:
        executer.withStackTraceChecksDisabled().withBuildCacheEnabled()
        run "producesLink"
        then:
        executedAndNotSkipped ":producesLink"
        outputContains "Could not pack snapshot 'tree-outputDirectory/link'"
    }
}
