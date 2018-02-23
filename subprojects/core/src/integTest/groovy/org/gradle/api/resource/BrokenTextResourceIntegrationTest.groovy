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

class BrokenTextResourceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
class TextTask extends DefaultTask {
    @Nested
    TextResource text

    @TaskAction
    def go() {
        println text.asString()
    }
}

task text(type: TextTask)
"""
    }

    def "reports read of missing text file"() {
        given:
        buildFile << """
            text.text = resources.text.fromFile('no-such-file')
"""

        expect:
        fails("text")

        def file = file('no-such-file')
        failure.assertHasCause("Could not read '${file}' as it does not exist.")
    }

    def "reports read of missing archive"() {
        given:
        buildFile << """
            text.text = resources.text.fromArchiveEntry('no-such-file', 'config.txt')
"""

        expect:
        fails("text")

        def file = file('no-such-file')
        failure.assertHasCause("Could not read '${file}' as it does not exist.")
    }

    def "reports read of missing archive entry"() {
        given:
        buildFile << """
            task jar(type: Jar)
            text.text = resources.text.fromArchiveEntry(jar, 'config.txt')
"""

        expect:
        fails("text")
        failure.assertHasCause("Expected entry 'config.txt' in archive file collection to contain exactly one file, however, it contains no files.")
    }
}
