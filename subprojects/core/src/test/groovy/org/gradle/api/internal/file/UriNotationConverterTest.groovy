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

package org.gradle.api.internal.file


import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class UriNotationConverterTest extends Specification {

    @Rule
    public TestNameTestDirectoryProvider folder = new TestNameTestDirectoryProvider(getClass());

    def "files are skipped"() {
        setup:
        def testFile = folder.createFile("test1")
        when:
        parse(testFile)
        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, testFile)
    }

    def "file strings are skipped"() {
        setup:
        def testFile = folder.createFile("test1")
        when:
        parse(testFile.getAbsolutePath())
        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, testFile.getAbsolutePath())
    }

    def "file URIs returned as is"() {
        setup:
        def testFileURI = folder.createFile("test1").toURI()
        when:
        def result = parse(testFileURI)
        then:
        result == testFileURI
    }

    def "file URIs as CharSequences are skipped"() {
        setup:
        def uriString = folder.createFile("test1").toURI().toString()
        when:
        parse(uriString)
        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, uriString)
    }

    def "file URLs returned as URIs"() {
        setup:
        def testFileURL = folder.createFile("test1").toURI().toURL()
        when:
        def result = parse(testFileURL)
        then:
        result == testFileURL.toURI()
    }

    def "non-file URI is returned"() {
        setup:
        def uri = URI.create("http://gradle.org")
        when:
        def parsed = parse(uri)
        then:
        parsed == uri
    }

    def "non-file URI as string is returned"() {
        setup:
        def uri = URI.create("http://gradle.org")
        when:
        def parsed = parse(uri.toString())
        then:
        parsed == uri
    }

    def "returns URI for unknown schema"() {
        setup:
        def uri = URI.create("no-schema")
        when:
        def parsed = parse(uri)
        then:
        parsed == uri
    }

    def "returns URI for non-hierarchical URI"() {
        setup:
        def uri = URI.create("sss::something")
        when:
        def parsed = parse(uri.toString())
        then:
        parsed == uri
    }

    @Issue("GRADLE-2072")
    def "parsing unknown types causes UnsupportedNotationException"() {
        when:
        parse(12)

        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, 12)
    }

    def parse(def value) {
        return UriNotationConverter.parser().parseNotation(value)
    }

    private void showsProperMessage(UnsupportedNotationException e, value) {
        assert e.message == toPlatformLineSeparators("""Cannot convert the provided notation to a URI: $value.
The following types/formats are supported:
  - A URI or URL instance.
  - A String or CharSequence URI, for example "http://www.gradle.org".""")
    }
}
