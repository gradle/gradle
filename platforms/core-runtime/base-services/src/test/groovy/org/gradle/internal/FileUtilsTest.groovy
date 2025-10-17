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

package org.gradle.internal

import com.google.common.base.Utf8
import org.apache.commons.lang3.RandomStringUtils
import org.gradle.api.GradleException
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static FileUtils.assertInWindowsPathLengthLimitation
import static FileUtils.toSafeFileName
import static org.gradle.internal.FileUtils.MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
import static org.gradle.internal.FileUtils.calculateRoots
import static org.gradle.internal.FileUtils.withExtension

class FileUtilsTest extends Specification {

    private static final String SEP = File.separator

    def "toSafeFileName preserves Unicode and replaces problematic characters"() {
        expect:
        toSafeFileName(input) == output
        where:
        input           | output
        'Test_$1-2.3'   | 'Test_$1-2.3'
        'with space'    | 'with-space'
        'with #'        | 'with-#'
        'with /'        | 'with--'
        'with \\'       | 'with--'
        'with / \\ #'   | 'with-----#'
        'with\tspace'   | 'with-space'
        'with\nline'    | 'with-line'
        'with\rreturn'  | 'with-return'
        '한글테스트'     | '한글테스트'
        'Test 中文'      | 'Test-中文'
        'Gradle Test Executor 1' | 'Gradle-Test-Executor-1'
    }

    def "assertInWindowsPathLengthLimitation throws exception when path limit exceeded"() {
        when:
        File inputFile = new File(RandomStringUtils.randomAlphanumeric(10))
        then:
        inputFile == assertInWindowsPathLengthLimitation(inputFile);

        when:
        inputFile = new File(RandomStringUtils.randomAlphanumeric(261))
        assertInWindowsPathLengthLimitation(inputFile);
        then:
        def e = thrown(GradleException);
        e.message.contains("exceeds windows path limitation of 260 character.")
    }

    List<File> toRoots(Iterable<? extends File> files) {
        calculateRoots(files)
    }

    List<File> files(String... paths) {
        paths.collect { new File("/", it).absoluteFile }
    }

    def "can find roots when leafs are directories"() {
        expect:
        toRoots([]) == []
        toRoots(files("a/a", "a/a")) == files("a/a")
        toRoots(files("a", "b", "c")) == files("a", "b", "c")
        toRoots(files("a/a", "a/a/a", "a/b/a")) == files("a/a", "a/b/a")
        toRoots(files("a/a/a", "a/a", "a/b/a")) == files("a/a", "a/b/a")
        toRoots(files("a/a", "a/a-1", "a/a/a")) == files("a/a", "a/a-1")
        toRoots(files("a/a", "a/a/a", "b/a/a")) == files("a/a", "b/a/a")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b")) == files("a/a/a/a/a/a/a/a/a", "a/b")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a")) == files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a", "b/a/a/a/a")) == files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a")
    }

    def "can transform filenames to alternate extensions"() {
        expect:
        withExtension("foo", ".bar") == "foo.bar"
        withExtension("/some/path/to/foo", ".bar") == "/some/path/to/foo.bar"
        withExtension("foo.baz", ".bar") == "foo.bar"
        withExtension("/some/path/to/foo.baz", ".bar") == "/some/path/to/foo.bar"
        withExtension("\\some\\path\\to\\foo.baz", ".bar") == "\\some\\path\\to\\foo.bar"
        withExtension("/some/path/to/foo.boo.baz", ".bar") == "/some/path/to/foo.boo.bar"
        withExtension("/some/path/to/foo.bar", ".bar") == "/some/path/to/foo.bar"
    }

    def "can determine if one path start with another"(String path, String startsWithPath, boolean result) {
        expect:
        FileUtils.doesPathStartWith(path, startsWithPath) == result

        where:
        path              | startsWithPath || result
        ""                | ""             || true
        "a${SEP}a${SEP}a" | "a${SEP}b"     || false
        "a${SEP}a"        | "a${SEP}a"     || true
        "a${SEP}a${SEP}a" | "a${SEP}a"     || true
        "a${SEP}ab"       | "a${SEP}a"     || false
    }

    def "can add suffix to filename"() {
        expect:
        FileUtils.addSuffixToName(original, suffix) == result

        where:
        original            | suffix     | result
        "file.zip"          | "-1"       | "file-1.zip"
        "file.tar.gz"       | "-bla-bla" | "file-bla-bla.tar.gz"
        "file.with.dots.gz" | "-2"       | "file-2.with.dots.gz"
        "file"              | "-1"       | "file-1"
        "file"              | ""         | "file"
        "file."             | "-2"       | "file-2."
    }

    def "toSafeFileName handles edge cases"() {
        expect:
        toSafeFileName(input) == output
        where:
        input                                    | output
        ''                                       | ''
        '   '                                    | '---'
        'normal'                                 | 'normal'
        '...'                                    | '...'
        'file:name'                              | 'file-name'
        'file<>name'                             | 'file--name'
        'file|name'                              | 'file-name'
        'file"name'                              | 'file-name'
        'file*name'                              | 'file-name'
        'file?name'                              | 'file-name'
        'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
    }

    def "toSafeFileName limits length based on bytes, not chars"() {
        when:
        def stringWithExactly255Chars = ('A' * 254) + 'Θ'
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactly255Chars.length() == 255
        toSafeFileName(stringWithExactly255Chars).getBytes(StandardCharsets.UTF_8).length == 255

        when:
        def stringWithOneMoreThanMaxSafeBytes = 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1)
        then:
        toSafeFileName(stringWithOneMoreThanMaxSafeBytes).getBytes(StandardCharsets.UTF_8).length == 255

        when:
        def stringWithExactlyMaxSafeBytesWithUnicode = 'A' + ('Θ' * ((MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) / 2))
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactlyMaxSafeBytesWithUnicode.getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
        toSafeFileName(stringWithExactlyMaxSafeBytesWithUnicode).getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
    }

    def "toSafeFileName hashes overly long paths"() {
        expect:
        toSafeFileName(input) == output
        where:
        input                       | output
        'A' * 256                   | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-9G531233RIUS4'
        ('A' * 253) + 'Θ'           | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-JJTN3CQ249KPK'
        // Hash should preserve extension
        ('A' * 256) + '.html'       | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html')) + '-EG3TV8Q6QCCQS.html'
        ('A' * 256) + '.Θ'          | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.Θ')) + '-N32UNHUNL5N5O.Θ'
        'Θ' + ('A' * 300) + '.html' | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.html'))) + '-5O3MPEP5S1RIC.html'
        'Θ' + ('A' * 300) + '.Θ'    | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.Θ'))) + '-27045PA297J5I.Θ'
        // Extension is only preserved if it fits, otherwise normal truncation occurs.
        'A.' + ('B' * 300)          | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 'A.'.length())) + '-3CF50NO32LPNI'
    }

    def "toSafeFileName does not create invalid UTF-8 when truncating"() {
        when:
        def stringWithUnicodeThatSitsOnByteLimit = ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1)) + 'Θ'
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithUnicodeThatSitsOnByteLimit.getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1
        // The truncation should remove the multi-byte character to avoid invalid UTF-8
        // resulting in a string of 254 bytes, not 255 bytes
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit).getBytes(StandardCharsets.UTF_8).length == 254
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit) == 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) + '-VIAJC1C2BG5UO'
    }

    def "toSafeFileName handles null input"() {
        when:
        toSafeFileName(null)

        then:
        thrown(NullPointerException)
    }
}
