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

import org.gradle.internal.SystemProperties
import org.gradle.util.internal.WrapUtil
import spock.lang.Specification
import spock.lang.Subject

public class JavadocOptionFileWriterContextTest extends Specification {

    def writer = new StringWriter()
    @Subject context = new JavadocOptionFileWriterContext(writer)

    def "writes"() {
        when: context.write("dummy")
        then: writer.toString() == "dummy"
    }

    def "writes new line"() {
        when: context.write("a").newLine().write("b")
        then: writer.toString() == "a${SystemProperties.instance.getLineSeparator()}b"
    }

    def "quotes and escapes"() {
        when: context.writeValueOption("key", "1\\2\\")
        then: writer.toString() == "-key '1\\\\2\\\\'${SystemProperties.instance.getLineSeparator()}"
    }

    def "quotes and escapes multiple values"() {
        when:
        context.writeValuesOption("key", WrapUtil.toList("a\\b", "c"), ":")

        then: writer.toString() == "-key 'a\\\\b:c'${SystemProperties.instance.getLineSeparator()}"
    }

    def "writes multiline value"() {
        when:
        context.writeValueOption("key", "Hey${SystemProperties.instance.getLineSeparator()}Joe!")

        then: writer.toString() == "-key 'Hey\\${SystemProperties.instance.getLineSeparator()}Joe!'${SystemProperties.instance.getLineSeparator()}"
    }
}
