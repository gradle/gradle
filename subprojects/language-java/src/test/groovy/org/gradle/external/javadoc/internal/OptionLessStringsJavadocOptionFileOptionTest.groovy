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

import com.google.common.collect.Lists
import spock.lang.Specification

class OptionLessStringsJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()

    private OptionLessStringsJavadocOptionFileOption optionLessStringsOption = new OptionLessStringsJavadocOptionFileOption(Lists.<String>newArrayList())

    def writeNonNullValue() throws IOException {
        final String firstValue = "firstValue"
        final String secondValue = "secondValue"
        optionLessStringsOption.value = [firstValue, secondValue]

        when:
        optionLessStringsOption.write(writerContextMock)

        then:
        1 * writerContextMock.writeValue(firstValue) >> writerContextMock
        1 * writerContextMock.newLine() >> writerContextMock
        1 * writerContextMock.writeValue(secondValue) >> writerContextMock
        1 * writerContextMock.newLine() >> writerContextMock
    }

}
