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

import com.google.common.collect.Lists
import spock.lang.Specification

class MultilineStringsJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()
    private final String optionName = "testOption"

    private MultilineStringsJavadocOptionFileOption linksOption

    def setup() {
        linksOption = new MultilineStringsJavadocOptionFileOption(optionName, Lists.<String>newArrayList())
    }

    def writeNullValue() {
        when:
        linksOption.writeCollectionValue(writerContextMock)

        then:
        0 * _._
    }

    def writeNonNullValue() {
        final String extDocUrl = "extDocUrl"
        linksOption.getValue().add(extDocUrl)

        when:
        linksOption.writeCollectionValue(writerContextMock)

        then:
        1 * writerContextMock.writeMultilineValuesOption(optionName, [extDocUrl])
    }

    def writeMultipleValues() {
        final String docUrl1 = "docUrl1"
        final String docUrl2 = "docUrl2"

        linksOption.getValue().add(docUrl1)
        linksOption.getValue().add(docUrl2)

        when:
        linksOption.writeCollectionValue(writerContextMock)

        then:
        1 * writerContextMock.writeMultilineValuesOption(optionName, [docUrl1, docUrl2])
    }
}
