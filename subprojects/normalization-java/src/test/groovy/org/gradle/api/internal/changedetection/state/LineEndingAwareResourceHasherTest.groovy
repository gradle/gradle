/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext
import org.gradle.internal.fingerprint.hashing.ResourceHasher
import org.gradle.internal.fingerprint.hashing.ZipEntryContext
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset


class LineEndingAwareResourceHasherTest extends Specification {
    private static final byte[] PNG_CONTENT = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] as byte[]
    private static final byte[] JPG_CONTENT = [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xda] as byte[]
    private static final byte[] CLASS_FILE_CONTENT = [0xca, 0xfe, 0xba, 0xbe, 0x00, 0x00, 0x00, 0x37, 0x0a, 0x00] as byte[]

    @Rule
    TemporaryFolder tempDir = new TemporaryFolder()

    @Unroll
    def "can normalize line endings in files (eol = '#description')"() {
        def unnormalized = file('unnormalized.txt') << textWithLineEndings(eol)
        def normalized = file('normalized.txt') << textWithLineEndings('\n')
        def hasher = new LineEndingAwareResourceHasher(new RuntimeClasspathResourceHasher(), LineEndingSensitivity.IGNORE_LINE_ENDINGS)

        expect:
        hasher.hash(snapshotContext(unnormalized)) == hasher.hash(snapshotContext(normalized))

        where:
        eol     | description
        '\r'    | 'CR'
        '\r\n'  | 'CR-LF'
        '\n'    | 'LF'
    }

    @Unroll
    def "can normalize line endings in zip entries (eol = '#description')"() {
        def unnormalized = file('unnormalized.txt') << textWithLineEndings(eol)
        def normalized = file('normalized.txt') << textWithLineEndings('\n')
        def hasher = new LineEndingAwareResourceHasher(new RuntimeClasspathResourceHasher(), LineEndingSensitivity.IGNORE_LINE_ENDINGS)

        expect:
        hasher.hash(zipContext(unnormalized)) == hasher.hash(zipContext(normalized))

        where:
        eol     | description
        '\r'    | 'CR'
        '\r\n'  | 'CR-LF'
        '\n'    | 'LF'
    }

    @Unroll
    def "handles read when #description"() {
        def stream = inputStream(text)

        expect:
        readAllBytes(stream, 8) == normalizedText.bytes

        where:
        text              | normalizedText  | description
        "\r1234567"       | "\n1234567"     | "first character in stream is a line ending"
        "\r\n1234567"     | "\n1234567"     | "first character in stream is a multi-character line ending"
        "1234567\r"       | "1234567\n"     | "last character in stream is a line ending"
        "1234567\r\n"     | "1234567\n"     | "last character in stream is a multi-character line ending"
        "1234567\r1234"   | "1234567\n1234" | "last character in buffer is a line ending"
        "123456\r\n1234"  | "123456\n1234"  | "last character in buffer is a multi-character line ending"
        "1234567\r\n1234" | "1234567\n1234" | "multi-character line ending crosses buffer boundary"
    }

    @Unroll
    def "calculates hash for text file with #description"() {
        def file = file('foo') << content
        def delegate = Mock(ResourceHasher)
        def hasher = new LineEndingAwareResourceHasher(delegate, LineEndingSensitivity.IGNORE_LINE_ENDINGS)

        when:
        hasher.hash(snapshotContext(file))

        then:
        0 * delegate._

        when:
        hasher.hash(zipContext(file))

        then:
        0 * delegate._

        where:
        description               | content
        "new lines"               | "this is\na text file\n".bytes
        "new lines with CR-LF"    | "this is\r\na text file\r\n".bytes
        "no new lines"            | "No new lines\tin this file".bytes
        "utf8 content"            | "here's some UTF8 content: €ЇΩ".getBytes(Charset.forName("UTF-8"))
    }

    @Unroll
    def "calls delegate for binary files with #description"() {
        def file = file('foo') << content
        def delegate = Mock(ResourceHasher)
        def hasher = new LineEndingAwareResourceHasher(delegate, LineEndingSensitivity.IGNORE_LINE_ENDINGS)
        def snapshotContext = snapshotContext(file)
        def zipContext = zipContext(file)

        when:
        hasher.hash(snapshotContext)

        then:
        1 * delegate.hash(snapshotContext)

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext)

        where:
        description               | content
        "png content"             | PNG_CONTENT
        "jpg content"             | JPG_CONTENT
        "java class file content" | CLASS_FILE_CONTENT
    }

    def "always calls delegate when line ending sensitivity is set to DEFAULT"() {
        def file = file('foo') << textWithLineEndings('\r\n')
        def delegate = Mock(ResourceHasher)
        def hasher = new LineEndingAwareResourceHasher(delegate, LineEndingSensitivity.DEFAULT)
        def snapshotContext = snapshotContext(file)
        def zipContext = zipContext(file)

        when:
        hasher.hash(snapshotContext)

        then:
        1 * delegate.hash(snapshotContext)

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext)
    }

    def "always calls delegate for directories"() {
        def delegate = Mock(ResourceHasher)
        def hasher = new LineEndingAwareResourceHasher(delegate, lineEndingSensitivity)
        def dir = file('dir')
        def snapshotContext = snapshotContext(dir, FileType.Directory)
        def zipContext = zipContext(dir, true)

        when:
        hasher.hash(snapshotContext)

        then:
        1 * delegate.hash(snapshotContext)

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext)

        where:
        lineEndingSensitivity << LineEndingSensitivity.values()
    }

    static String textWithLineEndings(String eol) {
        return "${eol}This is a line${eol}Another line${eol}${eol}Yet another line\nAnd one more\n\nAnd yet one more${eol}${eol}"
    }

    File file(String path) {
        return tempDir.newFile(path)
    }

    static ZipEntryContext zipContext(File file, boolean directory = false) {
        def zipEntry = new ZipEntry() {
            @Override
            boolean isDirectory() {
                return directory
            }

            @Override
            String getName() {
                return file.name
            }

            @Override
            byte[] getContent() throws IOException {
                return file.bytes
            }

            @Override
            <T> T withInputStream(ZipEntry.InputStreamAction<T> action) throws IOException {
                action.run(new ByteArrayInputStream(file.bytes))
            }

            @Override
            int size() {
                return file.bytes.length
            }
        }
        return new DefaultZipEntryContext(zipEntry, file.path, "foo.zip")
    }

    RegularFileSnapshotContext snapshotContext(File file, FileType fileType = FileType.RegularFile) {
        return RegularFileSnapshotContext.from(snapshot(file, fileType))
    }

    RegularFileSnapshot snapshot(File file, FileType fileType) {
        return Mock(RegularFileSnapshot) {
            getAbsolutePath() >> file.absolutePath
            getType() >> fileType
            getHash() >> Hashing.hashFile(file)
        }
    }

    static InputStream inputStream(String input) {
        return inputStream(input.bytes)
    }

    static InputStream inputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes)
    }

    static byte[] readAllBytes(InputStream inputStream, int bufferLength) {
        def streamHasher = new LineEndingAwareResourceHasher.LineEndingAwareInputStreamHasher()
        ArrayList<Byte> bytes = []
        byte[] buffer = new byte[bufferLength]
        int read
        while ((read = streamHasher.read(inputStream, buffer)) != -1) {
            bytes.addAll(buffer[0..(read-1)].collect { Byte.valueOf(it) })
        }
        return bytes as byte[]
    }
}
