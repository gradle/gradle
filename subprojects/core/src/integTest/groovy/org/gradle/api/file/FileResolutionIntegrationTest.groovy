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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileResolutionIntegrationTest extends AbstractIntegrationSpec {
    def "file conversion works with java.nio.file.Path"() {
        buildFile << """
java.nio.file.Path fAsPath = buildDir.toPath().resolve('testdir').toAbsolutePath()
def f = file(fAsPath)
assert f == fAsPath.toFile()
"""

        expect:
        succeeds()
    }

    def "file conversion works with Provider of supported types"() {
        buildFile << """
def provider = project.provider { $expression }
def f = file(provider)
assert f == file("testdir")
"""

        expect:
        succeeds()

        where:
        expression                        | _
        "new File(projectDir, 'testdir')" | _
        "new File('testdir').toPath()"    | _
        "'testdir'"                       | _
    }

    def "gives reasonable error message when value cannot be converted to file"() {
        buildFile << """
def f = file(12)
"""

        when:
        fails()

        then:
        failure.assertHasCause("""Cannot convert the provided notation to a File or URI: 12.
The following types/formats are supported:
  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
  - A String or CharSequence URI, for example 'file:/usr/include'.
  - A File instance.
  - A Path instance.
  - A URI or URL instance.""")
    }

    def "can construct file collection using java.nio.file.Path"() {
        buildFile << """
java.nio.file.Path fAsPath = buildDir.toPath().resolve('testdir').toAbsolutePath()
def f = files(fAsPath)
assert f.files as List == [fAsPath.toFile()]
"""

        expect:
        succeeds()
    }

    def "can construct file collection using Provider of supported types"() {
        buildFile << """
def provider = project.provider { $expression }
def f = files(provider)
assert f.files as List == [file("testdir")]
"""

        expect:
        succeeds()

        where:
        expression                        | _
        "new File(projectDir, 'testdir')" | _
        "new File('testdir').toPath()"    | _
        "'testdir'"                       | _
    }

    def "gives reasonable error message when value cannot be converted to file collection"() {
        buildFile << """
def f = files({[12]})
f.files.each { println it }
"""

        when:
        fails()

        then:
        failure.assertHasCause("""Cannot convert the provided notation to a File or URI: 12.
The following types/formats are supported:
  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
  - A String or CharSequence URI, for example 'file:/usr/include'.
  - A File instance.
  - A Path instance.
  - A URI or URL instance.""")
    }
}
