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

import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.Charset

import org.gradle.api.internal.changedetection.state.LineEndingContentFixture as content

class LineEndingNormalizingFileSystemLocationSnapshotHasherTest extends Specification {
    @TempDir
    File tempDir

    def "calculates hash for text file with #description"() {
        def file = file('foo') << contents
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)

        when:
        hasher.hash(snapshot(file))

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
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def snapshot = this.snapshot(file)

        when:
        hasher.hash(snapshot)

        then:
        1 * delegate.hash(snapshot)

        where:
        description               | contents
        "png content"             | content.PNG_CONTENT
        "jpg content"             | content.JPG_CONTENT
        "java class file content" | content.CLASS_FILE_CONTENT
    }

    def "always calls delegate when line ending sensitivity is set to DEFAULT"() {
        def file = file('foo') << content.textWithLineEndings('\r\n')
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, LineEndingSensitivity.DEFAULT)
        def snapshot = this.snapshot(file)

        when:
        hasher.hash(snapshot)

        then:
        1 * delegate.hash(snapshot)
    }

    def "always calls delegate for directories"() {
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, lineEndingSensitivity)
        def dir = file('dir').tap { it.text = "" }
        def snapshot = snapshot(dir, FileType.Directory)

        when:
        hasher.hash(snapshot)

        then:
        1 * delegate.hash(snapshot)

        where:
        lineEndingSensitivity << LineEndingSensitivity.values()
    }

    def "throws IOException generated from hasher"() {
        def file = file('doesNotExist').tap { it.text = "" }
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def snapshot = this.snapshot(file)

        when:
        assert hasher instanceof LineEndingNormalizingFileSystemLocationSnapshotHasher
        assert file.delete()
        hasher.hash(snapshot)

        then:
        thrown(FileNotFoundException)
    }

    def "throws #exception.simpleName generated from delegate"() {
        def file = file('doesNotExist') << content.PNG_CONTENT
        def delegate = Mock(LineEndingNormalizingFileSystemLocationSnapshotHasher)
        def hasher = LineEndingNormalizingFileSystemLocationSnapshotHasher.wrap(delegate, LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
        def snapshot = this.snapshot(file)

        when:
        assert hasher instanceof LineEndingNormalizingFileSystemLocationSnapshotHasher
        hasher.hash(snapshot)

        then:
        1 * delegate.hash(snapshot) >> { throw exception.getDeclaredConstructor().newInstance() }

        and:
        def e = thrown(thrownException)
        exception == thrownException || e.cause.class == exception

        where:
        exception        | thrownException
        IOException      | UncheckedIOException
        RuntimeException | RuntimeException
    }

    File file(String path) {
        return new File(tempDir, path)
    }

    RegularFileSnapshot snapshot(File file, FileType fileType = FileType.RegularFile) {
        return Mock(RegularFileSnapshot) {
            getAbsolutePath() >> file.absolutePath
            getType() >> fileType
            getHash() >> Hashing.hashFile(file)
        }
    }
}
