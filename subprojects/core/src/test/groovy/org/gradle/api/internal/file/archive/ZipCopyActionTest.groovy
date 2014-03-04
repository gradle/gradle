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

import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.internal.file.copy.ZipStoredCompressor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.Test
import spock.lang.Specification

import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions
import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit
import static org.hamcrest.Matchers.equalTo

class ZipCopyActionTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    ZipCopyAction visitor
    TestFile zipFile

    def setup() {
        zipFile = tmpDir.getTestDirectory().file("test.zip");
        visitor = new ZipCopyAction(zipFile, ZipStoredCompressor.INSTANCE_32);
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

        when:
        Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("dir", 2);
        expected.put("file", 1);

        then:
        assertVisitsPermissions(new ZipFileTree(zipFile, null), expected)
    }

    void wrapsFailureToOpenOutputFile() {
        given:
        def invalidZipFile = tmpDir.createDir("test.zip")
        visitor = new ZipCopyAction(invalidZipFile, ZipStoredCompressor.INSTANCE_32)

        when:
        visitor.execute(new CopyActionProcessingStream() {
            public void process(CopyActionProcessingStreamAction action) {
                // nothing
            }
        })

        then:
        def e = thrown(Exception)
        e.message == String.format("Could not create ZIP '%s'.", zipFile)
    }

    @Test
    public void wrapsFailureToAddElement() {
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
            public void process(CopyActionProcessingStreamAction action) {
                for (FileCopyDetailsInternal f : files) {
                    action.processFile(f);
                }
            }
        });
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
