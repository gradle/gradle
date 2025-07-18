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

import com.google.common.collect.Maps
import spock.lang.Specification

class GroupsJavadocOptionFileOptionTest extends Specification {
    private JavadocOptionFileWriterContext writerContextMock = Mock()
    private final String optionName = "testOption"

    private GroupsJavadocOptionFileOption groupsFile = new GroupsJavadocOptionFileOption(optionName, Maps.<String, List<String>>newLinkedHashMap())


    def testWriteNullValue() throws IOException {
        when:
        groupsFile.write(writerContextMock)

        then:
        0 * writerContextMock._
    }

    def testWriteNotNullValue() throws IOException {
        final String groupName = "testGroup"
        final List<String> groupElements = ["java.lang", "java.util*"]

        groupsFile.getValue().put(groupName, groupElements)

        when:
        groupsFile.write(writerContextMock)

        then:
        1 * writerContextMock.writeOptionHeader(optionName) >> writerContextMock
        1 * writerContextMock.write('"testGroup"') >> writerContextMock
        1 * writerContextMock.write(' ') >> writerContextMock
        1 * writerContextMock.write('"java.lang:java.util*"') >> writerContextMock
        1 * writerContextMock.newLine() >> writerContextMock
    }

}
