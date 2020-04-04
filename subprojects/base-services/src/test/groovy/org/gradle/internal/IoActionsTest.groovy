/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal

import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.IoActions.*

class IoActionsTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "can use file action to write to text file"() {
        given:
        def file = tmp.file("foo.txt")

        when:
        createTextFileWriteAction(file, "UTF-8").execute(new Action<Writer>() {
            void execute(Writer writer) {
                writer.write("bar")
            }
        })

        then:
        file.text == "bar"
    }

    def "fails to write to text file when can't create parent dir"() {
        given:
        tmp.createFile("base")
        def file = tmp.file("base/foo.txt")
        def action = Mock(Action)

        when:
        writeTextFile(file, "UTF-8", action)

        then:
        0 * action.execute(_)
        def e = thrown UncheckedIOException
        e.cause instanceof IOException
        e.cause.message.startsWith("Unable to create directory")
    }

    def "can write text file using specified encoding"() {
        given:
        def file = tmp.file("foo.txt")
        def enc = "utf-8"

        when:
        writeTextFile(file, enc, new Action() {
            void execute(writer) {
                writer.append("bar⌘")
            }
        })

        then:
        file.getText(enc) == "bar⌘"
    }

    def "can write text file with default encoding"() {
        given:
        def file = tmp.file("foo.txt")

        when:
        writeTextFile(file, new Action() {
            void execute(writer) {
                writer.append("bar⌘")
            }
        })

        then:
        file.text == "bar⌘"
    }

    def "closes resource after executing action"() {
        def action = Mock(Action)
        def resource = Mock(Closeable)

        when:
        withResource(resource, action)

        then:
        1 * action.execute(resource)

        then:
        1 * resource.close()
        0 * _._
    }

    def "closes resource after action fails"() {
        def action = Mock(Action)
        def resource = Mock(Closeable)
        def failure = new RuntimeException()

        when:
        withResource(resource, action)

        then:
        RuntimeException e = thrown()
        e.is(failure)

        then:
        1 * action.execute(resource) >> { throw failure }

        then:
        1 * resource.close()
        0 * _._
    }

    def "propagates failure to close resource"() {
        def action = Mock(Action)
        def resource = Mock(Closeable)
        def failure = new RuntimeException()

        when:
        withResource(resource, action)

        then:
        RuntimeException e = thrown()
        e.is(failure)

        then:
        1 * action.execute(resource)

        then:
        1 * resource.close() >> { throw failure }
        0 * _._
    }

    def "discards IOException thrown when closing resource after action fails"() {
        def action = Mock(Action)
        def resource = Mock(Closeable)
        def failure = new RuntimeException()

        when:
        withResource(resource, action)

        then:
        RuntimeException e = thrown()
        e.is(failure)

        then:
        1 * action.execute(resource) >> { throw failure }

        then:
        1 * resource.close() >> { throw new IOException("ignore me") }
        0 * _._
    }

    def "can handle null resource when closing"() {
        when:
        uncheckedClose(null)

        then:
        noExceptionThrown()

        when:
        closeQuietly(null)

        then:
        noExceptionThrown()
    }

    def "can close a valid resource"() {
        def resource = Mock(Closeable)

        when:
        uncheckedClose(resource)

        then:
        1 * resource.close()

        when:
        closeQuietly(resource)

        then:
        1 * resource.close()
    }

    def "rethrows unchecked exception when closing resource"() {
        def resource = Mock(Closeable)

        when:
        uncheckedClose(resource)

        then:
        1 * resource.close() >> { throw new IOException() }
        thrown(UncheckedIOException)
    }

    def "does not rethrow exception when closing resource"() {
        def resource = Mock(Closeable)

        when:
        closeQuietly(resource)

        then:
        1 * resource.close() >> { throw new IOException() }
        noExceptionThrown()
    }
}
