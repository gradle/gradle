/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.resource

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompressedResourceIntegrationTest extends AbstractIntegrationSpec {
    def "can read from bzip2 file"() {
        def sourceFile = testDirectory.file("source")
        def compressedFile = testDirectory.file("compressed")
        sourceFile.text = "hello"
        sourceFile.bzip2To(compressedFile)

        buildFile << """
def resource = resources.bzip2("compressed")

task show {
    doLast {
        def stream = resource.read()
        try { println stream.text } finally { stream.close() }
    }
}
"""

        when:
        run "show"

        then:
        output.contains("hello")
    }

    def "can read from gzip file"() {
        def sourceFile = testDirectory.file("source")
        def compressedFile = testDirectory.file("compressed")
        sourceFile.text = "hello"
        sourceFile.gzipTo(compressedFile)

        buildFile << """
def resource = resources.gzip("compressed")

task show {
    doLast {
        def stream = resource.read()
        try { println stream.text } finally { stream.close() }
    }
}
"""

        when:
        run "show"

        then:
        output.contains("hello")
    }
}
