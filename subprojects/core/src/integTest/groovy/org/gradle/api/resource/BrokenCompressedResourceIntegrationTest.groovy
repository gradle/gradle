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

class BrokenCompressedResourceIntegrationTest extends AbstractIntegrationSpec {
    def "reports missing bzip2 file when attempting to read"() {
        def file = file('compressed')
        buildFile << """
def resource = resources.bzip2("compressed")

task show {
    doLast {
        resource.read()
    }
}
"""

        when:
        fails "show"

        then:
        failure.assertHasCause("Could not read '$file' as it does not exist.")
    }

    def "reports broken bzip2 file when attempting to read"() {
        def file = file('compressed')
        file.text = "not compressed"

        buildFile << """
def resource = resources.bzip2("compressed")

task show {
    doLast {
        resource.read()
    }
}
"""

        when:
        fails "show"

        then:
        failure.assertHasCause("Could not read $file.")
    }

    def "reports missing gzip file when attempting to read"() {
        def file = file('compressed')
        buildFile << """
def resource = resources.gzip("compressed")

task show {
    doLast {
        resource.read()
    }
}
"""

        when:
        fails "show"

        then:
        failure.assertHasCause("Could not read '$file' as it does not exist.")
    }

    def "reports broken gzip file when attempting to read"() {
        def file = file('compressed')
        file.text = "not compressed"

        buildFile << """
def resource = resources.gzip("compressed")

task show {
    doLast {
        resource.read()
    }
}
"""

        when:
        fails "show"

        then:
        failure.assertHasCause("Could not read $file.")
    }
}
