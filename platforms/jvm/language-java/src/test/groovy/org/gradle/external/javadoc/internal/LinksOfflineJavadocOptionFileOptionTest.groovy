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
import org.gradle.external.javadoc.JavadocOfflineLink
import spock.lang.Specification

class LinksOfflineJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()
    private final String optionName = "testOption"

    private LinksOfflineJavadocOptionFileOption linksOfflineOption = new LinksOfflineJavadocOptionFileOption(optionName, Lists.<JavadocOfflineLink>newArrayList())


    def testWriteNullValue() throws IOException {
        when:
        linksOfflineOption.write(writerContextMock)

        then:
        0 * writerContextMock._
    }

    def writeNonNullValue() throws IOException {
        final String extDocUrl = "extDocUrl"
        final String packageListLoc = "packageListLoc"

        linksOfflineOption.getValue().add(new JavadocOfflineLink(extDocUrl, packageListLoc))

        when:
        linksOfflineOption.write(writerContextMock)

        then:
        1 * writerContextMock.writeOptionHeader(optionName) >> writerContextMock
        1 * writerContextMock.writeValue(extDocUrl) >> writerContextMock
        1 * writerContextMock.write(' ') >> writerContextMock
        1 * writerContextMock.writeValue(packageListLoc) >> writerContextMock
        1 * writerContextMock.newLine()

    }
}
