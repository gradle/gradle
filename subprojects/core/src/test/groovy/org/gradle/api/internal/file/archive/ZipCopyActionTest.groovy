/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.archive

import org.apache.tools.zip.Zip64RequiredException
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.DefaultZipCompressor
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.tasks.bundling.Zip
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.Test
import spock.lang.Specification

import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit
import static org.hamcrest.CoreMatchers.equalTo

class ZipCopyActionTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    ZipCopyAction visitor
    TestFile zipFile
    def encoding = 'UTF-8'

    def setup() {
        zipFile = tmpDir.getTestDirectory().file("test.zip")
        visitor = new ZipCopyAction(zipFile, new DefaultZipCompressor(false, ZipOutputStream.STORED), new DocumentationRegistry(), encoding, false)
    }

    void createsZipFile() {
        given:
        zip(dir("dir"), file("dir/file1"), file("file2"))

        when:
        TestFile expandDir = tmpDir.getTestDirectory().file("expanded")
        zipFile.unzipTo(expandDir)

        then:
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"))
        expandDir.file("file2").assertContents(equalTo("contents of file2"))
    }

    void createsDeflatedZipFile() {
        given:
        zip(dir("dir"), file("dir/file1"), file("file2"))

        when:
        TestFile expandDir = tmpDir.getTestDirectory().file("expanded")
        zipFile.unzipTo(expandDir)

        then:
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"))
        expandDir.file("file2").assertContents(equalTo("contents of file2"))
    }

    void zipFileContainsExpectedPermissions() {
        given:
        zip(dir("dir"), file("file"))

        expect:
        def zipFixture = new ZipTestFixture(zipFile)
        zipFixture.assertFileMode("dir/", 2)
        zipFixture.assertFileMode("file", 1)
    }

    void wrapsFailureToOpenOutputFile() {
        given:
        def invalidZipFile = tmpDir.createDir("test.zip")
        visitor = new ZipCopyAction(invalidZipFile, new DefaultZipCompressor(false, ZipOutputStream.STORED), new DocumentationRegistry(), encoding, false)

        when:
        visitor.execute(new CopyActionProcessingStream() {
            void process(CopyActionProcessingStreamAction action) {
                // nothing
            }
        })

        then:
        def e = thrown(Exception)
        e.message == String.format("Could not create ZIP '%s'.", zipFile)
    }

    void wrapsZip64Failure() {
        given:
        def zipOutputStream = Mock(ZipOutputStream)
        zipOutputStream.close() >> {
            throw new Zip64RequiredException("xyz")
        }

        def compressor = new DefaultZipCompressor(false, ZipOutputStream.STORED) {
            @Override
            ZipOutputStream createArchiveOutputStream(File destination) {
                zipOutputStream
            }
        }

        def docRegistry = Mock(DocumentationRegistry)
        1 * docRegistry.getDslRefForProperty(Zip, "zip64") >> "doc url"
        0 * docRegistry._

        visitor = new ZipCopyAction(zipFile, compressor, docRegistry, encoding, false)

        when:
        zip(file("file2"))

        then:
        def e = thrown(org.gradle.api.tasks.bundling.internal.Zip64RequiredException)
        e.message == "xyz\n\nTo build this archive, please enable the zip64 extension.\nSee: doc url"
    }

    @Test
    void wrapsFailureToAddElement() {
        given:
        Throwable failure = new RuntimeException("broken")

        def brokenFile = brokenFile("dir/file1", failure)
        when:
        visit(visitor, brokenFile)

        then:
        def e = thrown(Exception)
        e.message == String.format("Could not add $brokenFile to ZIP '%s'.", zipFile)
        e.cause.is(failure)
    }

    private void zip(final FileCopyDetailsInternal... files) {
        visitor.execute(new CopyActionProcessingStream() {
            void process(CopyActionProcessingStreamAction action) {
                for (FileCopyDetailsInternal f : files) {
                    action.processFile(f)
                }
            }
        })
    }

    private FileCopyDetailsInternal file(final String path) {
        def mock = Mock(FileCopyDetailsInternal)
        mock.getRelativePath() >> RelativePath.parse(false, path)
        mock.getLastModified() >> 1000L
        mock.isDirectory() >> false
        mock.getMode() >> 1
        mock.copyTo(_ as OutputStream) >> { OutputStream out ->
            out << "contents of $path"
        }
        mock
    }

    private FileCopyDetailsInternal dir(final String path) {
        def mock = Mock(FileCopyDetailsInternal)
        mock.getRelativePath() >> RelativePath.parse(false, path)
        mock.getLastModified() >> 1000L
        mock.isDirectory() >> true
        mock.getMode() >> 2
        mock
    }

    private FileCopyDetailsInternal brokenFile(final String path, final Throwable failure) {
        def mock = Mock(FileCopyDetailsInternal)
        mock.getRelativePath() >> RelativePath.parse(false, path)
        mock.getLastModified() >> 1000L
        mock.isDirectory() >> false
        mock.getMode() >> 1
        mock.copyTo(_ as OutputStream) >> { OutputStream out ->
            failure.fillInStackTrace()
            throw failure
        }
        mock
    }
}
