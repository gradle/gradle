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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.resources.TextResource
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class FileNotationConverterTest extends Specification {

    @Rule public TestNameTestDirectoryProvider folder = new TestNameTestDirectoryProvider(getClass());

    def "with File returns this File"() {
        setup:
        def testFile = folder.createFile("test1")
        when:
        def object = parse(testFile)
        then:
        testFile == object
    }

    def "with Path returns the File it represents"() {
        setup:
        def testPath = folder.createFile("test1").toPath()
        when:
        def object = parse(testPath)
        then:
        testPath.toFile() == object
    }

    def "with RegularFile returns the File it represents"() {
        setup:
        def testFile = folder.createFile("test1")
        def notation = Stub(RegularFile)
        notation.asFile >> testFile
        when:
        def object = parse(notation)
        then:
        object == testFile
    }

    def "with Directory returns the File it represents"() {
        setup:
        def testFile = folder.createFile("test1")
        def notation = Stub(Directory)
        notation.asFile >> testFile
        when:
        def object = parse(notation)
        then:
        object == testFile
    }

    def "with TextResource returns the underlying File"() {
        setup:
        def testFile = folder.createFile("test1")
        def notation = Stub(TextResource)
        notation.asFile() >> testFile
        when:
        def object = parse(notation)
        then:
        object == testFile
    }

    def "with file path as String"() {
        setup:
        def testFile = folder.createFile("test1")
        when:
        def object = parse(testFile.getAbsolutePath())
        then:
        testFile.getAbsolutePath() == object.getAbsolutePath()
    }

    def "with file URI"() {
        setup:
        def testFileURI = folder.createFile("test1").toURI()
        when:
        def object = parse(testFileURI)
        then:
        object.toURI() == testFileURI
    }

    def "with URI as CharSequence"() {
        setup:
        def uriString = folder.createFile("test1").toURI().toString()
        when:
        def object = parse(uriString)
        then:
        object.toURI().toString() == uriString
    }

    @Issue("https://github.com/gradle/gradle/issues/26678")
    def "does not change + in file scheme URI parsed from CharSequence"() {
        setup:
        def uriString = folder.createFile("test+1").toURI().toString()
        when:
        def object = parse(uriString)
        then:
        object.toURI().toString() == uriString
    }

    def "with URL"() {
        setup:
        def testFileURL = folder.createFile("test1").toURI().toURL()
        when:
        def object = parse(testFileURL)
        then:
        object.toURI().toURL() == testFileURL
    }

    def "with non File, an error is thrown"() {
        setup:
        def unsupportedURI = URI.create("http://gradle.org")
        when:
        parse(unsupportedURI)
        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, "http://gradle.org")
    }

    // See also BaseDirFileResolverTest.testCanResolveNonFileURI()
    def "with non File URI, String File is returned"() {
        setup:
        def uriString = "http://gradle.org"
        when:
        def parsed = parse(uriString)
        then:
        parsed == new File(uriString)
    }

    def "throws a error for URI with unknown schema"() {
        setup:
        def unsupportedURIString = new URI("no-schema")
        when:
        parse(unsupportedURIString)
        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, "no-schema")
    }

    // see also BaseDirFileResolverTest.testResolveRelativeFileURI
    def "does not throw NPE for non-hierarchical URI"() {
        setup:
        def unsupportedURIString = new URI("file::something")
        when:
        def parsed = parse(unsupportedURIString)
        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot convert URI 'file::something' to a file."
    }

    @Issue("GRADLE-2072")
    def "parsing unknown types causes UnsupportedNotationException"() {
        when:
        parse(12)

        then:
        UnsupportedNotationException e = thrown()
        showsProperMessage(e, 12)
    }

    private def parse(def value) {
        return FileNotationConverter.parser().parseNotation(value)
    }

    private void showsProperMessage(UnsupportedNotationException e, value) {
        assert e.message == toPlatformLineSeparators("""Cannot convert the provided notation to a File: $value.
The following types/formats are supported:
  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
  - A String or CharSequence URI, for example 'file:/usr/include'.
  - A File instance.
  - A Path instance.
  - A Directory instance.
  - A RegularFile instance.
  - A URI or URL instance of file.
  - A TextResource instance.""")
    }
}
