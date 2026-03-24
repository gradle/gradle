/*
 * Copyright 2025 the original author or authors.
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

import static org.gradle.internal.SafeFileLocationUtils.MAX_PATH_LENGTH
import static org.gradle.internal.SafeFileLocationUtils.MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
import static org.gradle.internal.SafeFileLocationUtils.assertInWindowsPathLengthLimitation
import static org.gradle.internal.SafeFileLocationUtils.toMinimumSafeFilePath
import static org.gradle.internal.SafeFileLocationUtils.toSafeFileName
import static org.gradle.internal.SafeFileLocationUtils.toSafeFilePath

class SafeFileLocationUtilsTest extends Specification {

    def "toSafeFileName preserves Unicode and replaces problematic characters"() {
        expect:
        toSafeFileName(input, false) == output
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

    def "toSafeFileName removes dots at end of directory name"() {
        expect:
        toSafeFileName(input, true) == output
        where:
        input           | output
        'folder.'       | 'folder'
        'folder..'      | 'folder'
        'folder...'     | 'folder'
        'folder.name.'  | 'folder.name'
        'folder.name..' | 'folder.name'
        '.foo'          | '.foo'
        '..foo'         | '..foo'
        '..'            | ''
        '.'             | ''
    }

    def "assertInWindowsPathLengthLimitation throws exception when path limit exceeded"() {
        when:
        File inputFile = new File(RandomStringUtils.secure().nextAlphanumeric(10))
        then:
        inputFile == assertInWindowsPathLengthLimitation(inputFile);

        when:
        inputFile = new File(RandomStringUtils.secure().nextAlphanumeric(261))
        assertInWindowsPathLengthLimitation(inputFile);
        then:
        def e = thrown(GradleException);
        e.message.contains("exceeds windows path limitation of 260 character.")
    }

    def "toSafeFileName handles edge cases"() {
        expect:
        toSafeFileName(input, false) == output
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
        'file\u0000name'                         | 'file-name'
        'file\ud800name'                         | 'file-name'
        'file\udfffname'                         | 'file-name'
        // Despite being a valid code point (https://www.unicode.org/versions/corrigendum9.html),
        // macOS rejects it anyways, so we replace it.
        'file\ufffename'                         | 'file-name'
        'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
    }

    def "toSafeFileName limits length based on bytes, not chars"() {
        when:
        def stringWithExactlyMaxSafeFileNameChars = ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1)) + 'Θ'
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactlyMaxSafeFileNameChars.length() == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
        // This gets shortened because it is at the byte limit, it comes out as 119 as the entire multi-byte char is removed
        toSafeFileName(stringWithExactlyMaxSafeFileNameChars, false).getBytes(StandardCharsets.UTF_8).length == 119

        when:
        def stringWithOneMoreThanMaxSafeBytes = 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1)
        then:
        toSafeFileName(stringWithOneMoreThanMaxSafeBytes, false).getBytes(StandardCharsets.UTF_8).length == 120

        when:
        def stringWithExactlyMaxSafeBytesWithUnicode = ('Θ' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES / 2)) +
            // Must add an extra character if the max length is odd to reach the exact byte length
            (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES % 2 == 0 ? '' : 'A')
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactlyMaxSafeBytesWithUnicode.getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
        toSafeFileName(stringWithExactlyMaxSafeBytesWithUnicode, false).getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
    }

    def "toSafeFileName hashes overly long paths"() {
        expect:
        toSafeFileName(input, false) ==  output
        where:
        input                          | output
        'A' * 256                      | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-OQG8MV3RAP2RE'
        ('A' * 253) + 'Θ'              | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-8PS0FSOFAJH0A'
        // Hash should preserve extension
        ('A' * 256) + '.html'          | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html')) + '-51HOT4U2CR0CI.html'
        ('A' * 256) + '.Θ'             | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.Θ')) + '-SIG1A0IU76DU6.Θ'
        'Θ' + ('A' * 300) + '.html'    | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.html'))) + '-IBA373SUCOLGQ.html'
        'Θ' + ('A' * 300) + '.Θ'       | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.Θ'))) + '-F9RNQ79NRFURK.Θ'
        // Extension is only preserved if it fits, otherwise normal truncation occurs.
        'A.' + ('B' * 300)             | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.'))) + '-0L8769223FCCI'
        // Extension fits, but requires truncation of preceding bytes
        'AAA.' + lessThanMax('B')      | 'A-IRC9V5NU92FVQ.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 2))
        // Preserves multiple extensions
        ('A' * 256) + '.html.gz'       | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html.gz')) + '-HTFEQN37FA9G2.html.gz'
        // But only as many as will fit
        'A.' + ('B' * 300) + '.tar.gz' | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.' + '.tar.gz'))) + '-RRID8S8J186QG.tar.gz'
    }

    private static final String PREFIX = 'FOO-'
    private static final int MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES = MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength(PREFIX)

    def "toSafeFileName hashes overly long paths and preserves a prefix when specified"() {
        expect:
        toSafeFileName(PREFIX, input, false) ==  output
        where:
        input                                                  | output
        'A' * 256                                              | PREFIX + 'A' * MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES + '-OQG8MV3RAP2RE'
        ('A' * 253) + 'Θ'                                      | PREFIX + 'A' * MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES + '-8PS0FSOFAJH0A'
        // Hash should preserve extension
        ('A' * 256) + '.html'                                  | PREFIX + 'A' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('.html')) + '-51HOT4U2CR0CI.html'
        ('A' * 256) + '.Θ'                                     | PREFIX + 'A' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('.Θ')) + '-SIG1A0IU76DU6.Θ'
        'Θ' + ('A' * 300) + '.html'                            | PREFIX + 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.html'))) + '-IBA373SUCOLGQ.html'
        'Θ' + ('A' * 300) + '.Θ'                               | PREFIX + 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.Θ'))) + '-F9RNQ79NRFURK.Θ'
        // Extension is only preserved if it fits, otherwise normal truncation occurs.
        'A.' + ('B' * 300)                                     | PREFIX + 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('A.'))) + '-0L8769223FCCI'
        // Extension fits, but requires truncation of preceding bytes which cannot preserve the prefix (i.e. preceding bytes are shorter than the prefix)
        'AA.' + lessThanMax('B')                               | PREFIX + 'AA.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('AA.'))) + "-AP25F54MG8EPI"
        // Extension fits, but requires truncation of preceding bytes which can still preserve the prefix
        moreThanPrefix('A') + '.' + lessThanMaxWithPrefix('B') | PREFIX + 'A' + "-2ISAEDM2QKNUE" + '.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('A.')))
        // Preserves multiple extensions
        ('A' * 256) + '.html.gz'                               | PREFIX + 'A' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('.html.gz')) + '-HTFEQN37FA9G2.html.gz'
        // But only as many as will fit
        'A.' + ('B' * 300) + '.tar.gz'                         | PREFIX + 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - Utf8.encodedLength('A.' + '.tar.gz'))) + '-RRID8S8J186QG.tar.gz'
    }

    static String lessThanMax(String character) {
        return character * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 2)
    }

    static String lessThanMaxWithPrefix(String character) {
        return character * (MAX_SAFE_FILE_NAME_LENGTH_WITH_PREFIX_IN_BYTES - 2)
    }

    static String moreThanPrefix(String character) {
        return character * Utf8.encodedLength(PREFIX) * 2
    }

    def "toSafeFileName throws exception when prefix is too long"() {
        when:
        toSafeFileName('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1), 'input', false)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Prefix length exceeds maximum safe file name length")
    }

    def "toSafeFileName does not create invalid UTF-8 when truncating"() {
        when:
        def stringWithUnicodeThatSitsOnByteLimit = ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1)) + 'Θ'
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithUnicodeThatSitsOnByteLimit.getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1
        // The truncation should remove the multi-byte character to avoid invalid UTF-8
        // resulting in a string of 119 bytes, not 120 bytes
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit, false).getBytes(StandardCharsets.UTF_8).length == 119
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit, false) == 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) + '-FETOQOJD1M8V0'
    }

    def "toSafeFileName handles null input"() {
        when:
        toSafeFileName(null, false)

        then:
        thrown(NullPointerException)
    }

    private static final int MAX_HASH_BYTE_LENGTH = 13

    def "PathLimitChecker returns WITHIN_LIMIT for short paths"() {
        expect:
        new SafeFileLocationUtils.PathLimitChecker('/base').check(buildSegmentsToFile('a', 'b', 'c.html')) == SafeFileLocationUtils.PathLimitCheckResult.WITHIN_LIMIT
    }

    def "PathLimitChecker returns EXCEEDS_LIMIT when path exceeds limit but can be shrunk"() {
        given:
        // Add one for '/'
        def basePath = basePathWithRoom(MAX_HASH_BYTE_LENGTH + 1 + Utf8.encodedLength('.html'))

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('file-that-makes-it-too-long.html')) == SafeFileLocationUtils.PathLimitCheckResult.EXCEEDS_LIMIT
    }

    def "PathLimitChecker returns EXCEEDS_LIMIT when path exceeds limit but can be shrunk even if only partially"() {
        given:
        // Add 1 for each '/'
        def basePath = basePathWithRoom(
            1 + MAX_HASH_BYTE_LENGTH + 1 + Utf8.encodedLength('file.html')
        )

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile(
            'dir-that-is-too-long-and-can-be-shrunk', 'file.html'
        )) == SafeFileLocationUtils.PathLimitCheckResult.EXCEEDS_LIMIT
    }

    def "PathLimitChecker returns UNSHRINKABLE when path cannot be shrunk enough"() {
        given:
        def basePath = basePathWithRoom(2)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('A' * 50, 'C' * 50)) == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE
    }

    def "PathLimitChecker returns UNSHRINKABLE when base path separators push path over limit"() {
        given:
        // '/a' is 2, leave room for only 1.
        def basePath = basePathWithRoom(1)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('a')) == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE
    }

    def "PathLimitChecker returns UNSHRINKABLE when directory separators push path over limit"() {
        given:
        // '/a/b' is 4, leave room for only 3.
        def basePath = basePathWithRoom(3)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('a', 'b')) == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE
    }

    def "PathLimitChecker returns UNSHRINKABLE when trailing slash push path over limit"() {
        given:
        // '/a/b/' is 5, leave room for only 4.
        def basePath = basePathWithRoom(4)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToDir('a', 'b')) == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE
    }

    def "PathLimitChecker counts UTF-8 byte length not character count"() {
        given:
        // 'ñ' is 2 bytes in UTF-8, so '/ñ' is 3 bytes but only 2 characters
        // Leave room for only 2 bytes — enough for characters but not bytes
        def basePath = basePathWithRoom(2)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('ñ')) == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE
    }

    def "PathLimitChecker returns EXCEEDS_LIMIT for Unicode segment that can be shrunk by hashing"() {
        given:
        // Leave room for '/' + the file name if it was counted as characters instead of bytes,
        // which is also enough room for the hash
        // Since the file name should be counted in bytes, it should be considered too long, but it can be shrunk by hashing
        def basePath = basePathWithRoom(20 + 1)

        expect:
        new SafeFileLocationUtils.PathLimitChecker(basePath).check(buildSegmentsToFile('ñ' * 20)) == SafeFileLocationUtils.PathLimitCheckResult.EXCEEDS_LIMIT
    }

    def "PathLimitChecker throws on relative base path"() {
        when:
        new SafeFileLocationUtils.PathLimitChecker('relative').check(buildSegmentsToFile('path'))

        then:
        thrown(IllegalArgumentException)
    }

    def "toSafeFilePath returns safe segments joined"() {
        expect:
        toSafeFilePath(buildSegmentsToFile('a', 'b', 'c.html')) == 'a/b/c.html'
        toSafeFilePath(buildSegmentsToFile('simple')) == 'simple'
        toSafeFilePath(buildSegmentsToFile('dir', 'file.txt')) == 'dir/file.txt'
    }

    def "toSafeFilePath sanitizes segment names"() {
        expect:
        toSafeFilePath(buildSegmentsToFile('with space', 'file name.html')) == 'with-space/file-name.html'
    }

    def "toSafeFilePath has handling based on if a segment is a directory"() {
        expect:
        // Trailing dots are removed for directories but not files
        toSafeFilePath(buildSegmentsToFile('a.', 'b.')) == 'a/b.'
        // All-directory paths get a trailing slash
        toSafeFilePath(buildSegmentsToDir('a.', 'b.')) == 'a/b/'
    }

    def "toSafeFilePath handles Unicode segments"() {
        expect:
        toSafeFilePath(buildSegmentsToFile('한글', 'テスト', 'file.html')) == '한글/テスト/file.html'
    }

    def "toSafeFilePath handles single segment path"() {
        expect:
        toSafeFilePath(buildSegmentsToFile('file.html')) == 'file.html'
    }

    def "toSafeFilePath handles empty segments with consecutive slashes"() {
        expect:
        toSafeFilePath(buildSegmentsToFile('a', '', 'b')) == 'a//b'
    }

    def "toSafeFilePath does not hash even long segments"() {
        given:
        def longName = 'A' * 50

        when:
        def result = toSafeFilePath(buildSegmentsToFile(longName, 'file.html'))

        then:
        result == longName + '/file.html'
    }

    def "toMinimumSafeFilePath hashes all segments that benefit from hashing"() {
        given:
        def longName = 'A' * 50

        when:
        def result = toMinimumSafeFilePath(buildSegmentsToFile(longName, longName + '.html'))
        def resultSegments = result.split('/')

        then:
        // Directory segment should be hashed to 13 chars
        resultSegments[0].length() == 13
        // File segment should be hashed but preserve the .html extension
        resultSegments[1].endsWith('.html')
        resultSegments[1].length() == 13 + '.html'.length()
    }

    def "toMinimumSafeFilePath does not hash short segments"() {
        expect:
        // Short segments where hash would be longer are preserved
        toMinimumSafeFilePath(buildSegmentsToFile('a', 'b.html')) == 'a/b.html'
    }

    def "toMinimumSafeFilePath hashes Unicode segments"() {
        given:
        def unicodeSegment = '한' * 30

        when:
        def result = toMinimumSafeFilePath(buildSegmentsToFile(unicodeSegment, 'file.html'))
        def resultSegments = result.split('/')

        then:
        // The file segment (9 bytes < 13-byte hash) is too short to hash, so it is preserved
        resultSegments.last() == 'file.html'
        // The Unicode directory segment is hashed
        !resultSegments[0].contains('한')
    }

    def "toMinimumSafeFilePath hashes Unicode segments if too long in byte length, but not char length"() {
        given:
        // Each is 3 bytes in UTF-8, so 6 chars is 18 bytes which exceeds the 13 byte hash length
        def unicodeSegment = '한' * 6

        when:
        def result = toMinimumSafeFilePath(buildSegmentsToFile(unicodeSegment, 'file.html'))
        def resultSegments = result.split('/')

        then:
        // The file segment (9 bytes < 13-byte hash) is too short to hash, so it is preserved
        resultSegments.last() == 'file.html'
        // The Unicode directory segment is hashed
        !resultSegments[0].contains('한')
    }

    private static String basePathWithRoom(int room) {
        return '/' + 'B' * (MAX_PATH_LENGTH - 1 - room)
    }

    private static List<SafeFileLocationUtils.Segment> buildSegmentsToFile(String... names) {
        assert names.length > 0 : "must have at least one segment for the file"
        def dirs = names[0..<-1].collect { SafeFileLocationUtils.Segment.directory(it) }
        return dirs + [SafeFileLocationUtils.Segment.file(names[-1])]
    }

    private static List<SafeFileLocationUtils.Segment> buildSegmentsToDir(String... names) {
        return names.collect { SafeFileLocationUtils.Segment.directory(it) }
    }
}
