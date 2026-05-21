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
import org.gradle.internal.io.IoFunction
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.Charset

import org.gradle.api.internal.changedetection.state.LineEndingContentFixture as content

class LineEndingNormalizingResourceHasherTest extends Specification {
    @TempDir
    File tempDir

    def "calculates hash for text file with #description"() {
        def file = file('foo') << contents
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)

        when:
        hasher.hash(snapshotContext(file))

        then:
        0 * delegate._

        when:
        hasher.hash(zipContext(file))

        then:
        0 * delegate._

        where:
        description               | contents
        "new lines"               | "this is\na text file\n".bytes
        "new lines with CR-LF"    | "this is\r\na text file\r\n".bytes
        "no new lines"            | "No new lines\tin this file".bytes
        "utf8 content"            | "here's some UTF8 content: €ЇΩ".getBytes(Charset.forName("UTF-8"))
    }

    def "calls delegate for binary files with #description"() {
        def file = file('foo') << contents
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
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
        description               | contents
        "png content"             | content.PNG_CONTENT
        "jpg content"             | content.JPG_CONTENT
        "java class file content" | content.CLASS_FILE_CONTENT
    }

    def "always calls delegate when line ending sensitivity is set to DEFAULT"() {
        def file = file('foo') << content.textWithLineEndings('\r\n')
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.DEFAULT)
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
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, lineEndingSensitivity)
        def dir = file('dir')
        def zipContext = zipContext(dir, true)

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext)

        where:
        lineEndingSensitivity << LineEndingSensitivity.values()
    }

    def "hashes original context with delegate for directories"() {
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def dir = file('dir')
        def zipContext = zipContext(dir, true, true)

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext)
    }

    def "throws IOException generated from hasher"() {
        def file = file('doesNotExist').tap { it.text = "" }
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def snapshotContext = snapshotContext(file)

        when:
        assert hasher instanceof LineEndingNormalizingResourceHasher
        assert file.delete()
        hasher.hash(snapshotContext)

        then:
        def e = thrown(UncheckedIOException)
        e.cause instanceof FileNotFoundException
    }

    def "throws #exception.simpleName generated from delegate"() {
        def file = file('doesNotExist') << content.PNG_CONTENT
        def delegate = Mock(ResourceHasher)
        def hasher = LineEndingNormalizingResourceHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def snapshotContext = snapshotContext(file)
        def zipContext = zipContext(file)

        when:
        assert hasher instanceof LineEndingNormalizingResourceHasher
        hasher.hash(snapshotContext)

        then:
        1 * delegate.hash(snapshotContext) >> { throw exception.getDeclaredConstructor().newInstance() }

        and:
        def e = thrown(thrownException)
        exception == thrownException || e.cause.class == exception

        when:
        hasher.hash(zipContext)

        then:
        1 * delegate.hash(zipContext) >> { throw exception.getDeclaredConstructor().newInstance() }

        and:
        e = thrown(thrownException)
        exception == thrownException || e.cause.class == exception

        where:
        exception           | thrownException
        IOException         | UncheckedIOException
        RuntimeException    | RuntimeException
    }

    File file(String path) {
        return new File(tempDir, path)
    }

    static ZipEntryContext zipContext(File file, boolean directory = false, boolean unsafe = false) {
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
            <T> T withInputStream(IoFunction<InputStream, T> action) throws IOException {
                action.apply(new ByteArrayInputStream(file.bytes))
            }

            @Override
            int size() {
                return file.bytes.length
            }

            @Override
            boolean canReopen() {
                return !unsafe
            }

            @Override
            ZipEntry.ZipCompressionMethod getCompressionMethod() {
                return ZipEntry.ZipCompressionMethod.DEFLATED
            }
        }
        return new DefaultZipEntryContext(zipEntry, file.path, "foo.zip")
    }

    RegularFileSnapshotContext snapshotContext(File file, FileType fileType = FileType.RegularFile) {
        return Mock(RegularFileSnapshotContext) {
            getSnapshot() >> snapshot(file, fileType)
        }
    }

    RegularFileSnapshot snapshot(File file, FileType fileType) {
        return Mock(RegularFileSnapshot) {
            getAbsolutePath() >> file.absolutePath
            getType() >> fileType
            getHash() >> Hashing.hashFile(file)
        }
    }
}
