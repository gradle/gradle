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

package org.gradle.normalization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.test.fixtures.archive.ArchiveBuilder.nestedArchive

class DeeplyNestedArchiveNormalizationIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/16893")
    def "can use an archive nested 5000 levels deep on the runtime classpath"() {
        given:
        // Deep enough that fingerprinting every level would exhaust the stack
        file("archive.jar").bytes = nestedArchive(5000)
        buildFile << """
            task customTask {
                def outputFile = file("build/output.txt")
                inputs.file("archive.jar")
                    .withPropertyName("classpath")
                    .withNormalizer(ClasspathNormalizer)
                outputs.file(outputFile)
                    .withPropertyName("outputFile")

                doLast {
                    outputFile.text = "done"
                }
            }
        """

        when:
        succeeds "customTask"

        then:
        executedAndNotSkipped(":customTask")
        file("build/output.txt").text == "done"

        when:
        succeeds "customTask"

        then:
        skipped(":customTask")
    }
}
