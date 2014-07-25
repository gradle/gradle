/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc.internal

import org.gradle.util.WrapUtil
import spock.lang.Specification
import spock.lang.Subject

public class JavadocOptionFileWriterContextTest extends Specification {

    def writer = Mock(BufferedWriter)
    @Subject context = new JavadocOptionFileWriterContext(writer)

    def "writes"() {
        when: context.write("dummy")
        then: writer.write("dummy")
    }

    def "writes new line"() {
        when: context.newLine()
        then: writer.newLine()
    }

    def "quotes and escapes"() {
        when:
        context.writeValueOption("key", "1\\2\\")

        then: writer.write("-")
        then: writer.write("key")
        then: writer.write(" ")
        then: writer.write("'")
        then: writer.write("1\\\\2\\\\")
        then: writer.write("'")
        then: writer.newLine()
    }

    def "quotes and escapes multiple values"() {
        when:
        context.writeValuesOption("key", WrapUtil.toList("a\\b", "c"), ":")

        then: writer.write("-")
        then: writer.write("key")
        then: writer.write(" ")
        then: writer.write("'")
        then: writer.write("a\\\\b:c")
        then: writer.write("'")
        then: writer.newLine()
    }

    def "writes multiline value"() {
        when:
        context.writeValueOption("key", """Hey
Joe!""")

        then: writer.write("-")
        then: writer.write("key")
        then: writer.write(" ")
        then: writer.write("'")
        then: writer.write("""Hey\
Joe!""")
        then: writer.write("'")
        then: writer.newLine()
    }
}