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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.IoActions.createTextFileWriteAction
import static org.gradle.api.internal.IoActions.writeTextFile

class IoActionsTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp

    def "can use file action to write to file"() {
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

    def "fails to write to file when can't create parent dir"() {
        given:
        tmp.createFile("base")
        def file = tmp.file("base/foo.txt")
        def action = Mock(Action)

        when:
        createTextFileWriteAction(file, "UTF-8").execute(action)

        then:
        0 * action.execute(_)
        def e = thrown UncheckedIOException
        e.cause instanceof IOException
        e.cause.message.startsWith("Unable to create directory")
    }

    def "can write file"() {
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

    def "can write file with default encoding"() {
        given:
        def file = tmp.file("foo.txt")

        when:
        writeFile(file, new Action() {
            void execute(writer) {
                writer.append("bar⌘")
            }
        })

        then:
        file.text == "bar⌘"
    }

}
