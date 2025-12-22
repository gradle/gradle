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

import static org.gradle.internal.SafeFileLocationUtils.MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
import static org.gradle.internal.SafeFileLocationUtils.assertInWindowsPathLengthLimitation
import static org.gradle.internal.SafeFileLocationUtils.toSafeFileName

class SafeFileLocationUtilsTest extends Specification {

    private static final String TRUNCATED_PREFIX = '__'

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
        toSafeFileName(input, false) == TRUNCATED_PREFIX + output
        where:
        input                       | output
        'A' * 256                   | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-RH50HNS6NT02C'
        ('A' * 253) + 'Θ'           | 'A' * MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + '-CDRIEFT37CF62'
        // Hash should preserve extension
        ('A' * 256) + '.html'       | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html')) + '-NR9BSEM4PR5K8.html'
        ('A' * 256) + '.Θ'          | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.Θ')) + '-3UVVBHCH79BP4.Θ'
        'Θ' + ('A' * 300) + '.html' | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.html'))) + '-G48FMD1TA7KI0.html'
        'Θ' + ('A' * 300) + '.Θ'    | 'Θ' + ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('Θ') - Utf8.encodedLength('.Θ'))) + '-47RJVSJSPTLNE.Θ'
        // Extension is only preserved if it fits, otherwise normal truncation occurs.
        'A.' + ('B' * 300)          | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.'))) + '-2MG4M8VQTCJRC'
        // Preserves multiple extensions
        ('A' * 256) + '.html.gz'       | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html.gz')) + '-3M67KJ1Q0I79C.html.gz'
        // But only as many as will fit
        'A.' + ('B' * 300) + '.tar.gz'  | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.' + '.tar.gz'))) + '-EMGQFU4IK3JLU.tar.gz'
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
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit, false) == TRUNCATED_PREFIX + 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) + '-R2C8IVGQO8MJ4'
    }

    def "toSafeFileName handles null input"() {
        when:
        toSafeFileName(null, false)

        then:
        thrown(NullPointerException)
    }
}
