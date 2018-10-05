/*
 * Copyright 2018 the original author or authors.
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

class MultilineMultiValueJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()
    private final String optionName = "testOption"

    private MultilineMultiValueJavadocOptionFileOption multilineMultiValueOption = new MultilineMultiValueJavadocOptionFileOption(optionName, Lists.<List<String>> newArrayList())

    def testWriteNullValue() throws IOException {
        when:
        multilineMultiValueOption.write(writerContextMock)

        then:
        0 * writerContextMock._
    }

    def writeNoneNullValue() throws IOException {
        final List<String> valueListOne = Lists.asList "valueOne-One", "valueOne-Two"
        final List<String> valueListTwo = Lists.asList "valueTwo-One", "valueTwo-Two"

        multilineMultiValueOption.getValue().add(valueListOne)
        multilineMultiValueOption.getValue().add(valueListTwo)

        when:
        multilineMultiValueOption.write(writerContextMock)

        then:
        1 * writerContextMock.writeMultilineMultiValueOption(optionName, multilineMultiValueOption.getValue())
    }
}
