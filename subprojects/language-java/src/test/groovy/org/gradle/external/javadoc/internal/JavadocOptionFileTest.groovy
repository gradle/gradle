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

import spock.lang.Specification

class JavadocOptionFileTest extends Specification {
    def optionFileOptionMock = Mock(JavadocOptionFileOptionInternal.class)
    def optionFile = new JavadocOptionFile()

    def "test defaults"() {
        expect:
        optionFile.getOptions() != null
        optionFile.getOptions().isEmpty()

        optionFile.getSourceNames() != null
        optionFile.getSourceNames().getValue() != null
        optionFile.getSourceNames().getValue().isEmpty()
    }

    def "test addOption"() {
        when:
        optionFile.addOption(optionFileOptionMock)

        then:
        noExceptionThrown()
        1 * optionFileOptionMock.getOption() >> "testOption"
    }
}
