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

package org.gradle.external.javadoc.internal

import org.gradle.external.javadoc.JavadocOptionFileOption
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class JavadocOptionFileWriterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    JavadocOptionFile optionfile = Mock()
    JavadocOptionFileWriter javadocOptionFileWriter = new JavadocOptionFileWriter(optionfile)

    def "writes locale option first if set"() {
        setup:
        def tempFile = temporaryFolder.createFile("optionFile")
        def optionsMap = createOptionsMap()
        when:
        _ * optionfile.options >> optionsMap
        _ * optionfile.getSourceNames() >> new OptionLessStringsJavadocOptionFileOption([]);
        javadocOptionFileWriter.write(tempFile)
        then:
        tempFile.text == toPlatformLineSeparators("""-key1 'value1'
-key2 'value2'
-key3 'value3'
""")
        when:
        optionsMap.put("locale", new StringJavadocOptionFileOption("locale", "alocale"));
        and:
        javadocOptionFileWriter.write(tempFile)
        then:
        tempFile.text == toPlatformLineSeparators("""-locale 'alocale'
-key1 'value1'
-key2 'value2'
-key3 'value3'
""")
    }

    Map<String, JavadocOptionFileOption> createOptionsMap() {
        Map<String, JavadocOptionFileOption> options = new HashMap<String, JavadocOptionFileOption>();
        options.put("key1", new StringJavadocOptionFileOption("key1", "value1"))
        options.put("key2", new StringJavadocOptionFileOption("key2", "value2"))
        options.put("key3", new StringJavadocOptionFileOption("key3", "value3"))
        return options
    }
}
