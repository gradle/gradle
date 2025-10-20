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

import static org.gradle.internal.SafeFileLocationUtils.assertInWindowsPathLengthLimitation
import static org.gradle.internal.SafeFileLocationUtils.toSafeFileName
import static org.gradle.internal.SafeFileLocationUtils.MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES

class SafeFileLocationUtilsTest extends Specification {

    private static final String TRUNCATED_PREFIX = '_cut_'

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
        def stringWithExactlyMaxSafeFileNameChars = ('A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1)) + 'Θ'
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactlyMaxSafeFileNameChars.length() == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
        // This gets shortened because it is at the byte limit, it comes out as 254 as the entire multi-byte char is removed
        toSafeFileName(stringWithExactlyMaxSafeFileNameChars).getBytes(StandardCharsets.UTF_8).length == 254

        when:
        def stringWithOneMoreThanMaxSafeBytes = 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES + 1)
        then:
        toSafeFileName(stringWithOneMoreThanMaxSafeBytes).getBytes(StandardCharsets.UTF_8).length == 255

        when:
        def stringWithExactlyMaxSafeBytesWithUnicode = ('Θ' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES / 2)) +
            // Must add an extra character if the max length is odd to reach the exact byte length
            (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES % 2 == 0 ? '' : 'A')
        then:
        // Prove our test string is what it says it is, since it may not be obvious to the reader
        stringWithExactlyMaxSafeBytesWithUnicode.getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
        toSafeFileName(stringWithExactlyMaxSafeBytesWithUnicode).getBytes(StandardCharsets.UTF_8).length == MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES
    }

    def "toSafeFileName hashes overly long paths"() {
        expect:
        toSafeFileName(input) == TRUNCATED_PREFIX + output
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
        'A.' + ('B' * 300)          | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.'))) + '-3CF50NO32LPNI'
        // Preserves multiple extensions
        ('A' * 256) + '.html.gz'       | 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('.html.gz')) + '-KFB4JD5BUIEPK.html.gz'
        // But only as many as will fit
        'A.' + ('B' * 300) + '.tar.gz'  | 'A.' + ('B' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - Utf8.encodedLength('A.' + '.tar.gz'))) + '-EEI7P4AQG1S6A.tar.gz'
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
        toSafeFileName(stringWithUnicodeThatSitsOnByteLimit) == TRUNCATED_PREFIX + 'A' * (MAX_SAFE_FILE_NAME_LENGTH_IN_BYTES - 1) + '-6HRSE15BOKJM4'
    }

    def "toSafeFileName handles null input"() {
        when:
        toSafeFileName(null)

        then:
        thrown(NullPointerException)
    }
}
