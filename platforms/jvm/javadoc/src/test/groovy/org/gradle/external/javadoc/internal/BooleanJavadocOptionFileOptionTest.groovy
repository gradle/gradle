/*
 * Copyright 2010 the original author or authors.
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


import spock.lang.Specification

class BooleanJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()
    private final String optionName = "testOption"
    private BooleanJavadocOptionFileOption booleanOption = new BooleanJavadocOptionFileOption(optionName, null)

    def testWriteNullValue() {
        when:
        booleanOption.write(writerContextMock)

        then:
        0 * _._
    }

    def testWriteFalseValue() throws IOException {
        booleanOption.setValue(false)

        when:
        booleanOption.write(writerContextMock)

        then:
        0 * _._
    }

    def testWriteTrueValue() throws IOException {
        booleanOption.setValue(true)

        when:
        booleanOption.write(writerContextMock)

        then:
        1 * writerContextMock.writeOption(optionName)
    }
}
