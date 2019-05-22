/*
 * Copyright 2016 the original author or authors.
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

import org.apache.commons.io.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.archive.compression.ArchiveOutputStreamFactory
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver
import org.gradle.api.internal.file.archive.compression.GzipArchiver
import org.gradle.api.internal.file.archive.compression.SimpleCompressor
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit
import static org.hamcrest.CoreMatchers.equalTo

@CleanupTestDirectory
public class TarCopyActionSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider();
    private TarCopyAction action;


    def "creates tar file"() {
        expect:
        final TestFile tarFile = initializeTarFile(temporaryFolder.testDirectory.file("test.tar"), new SimpleCompressor())
        tarAndUntarAndCheckFileContents(tarFile)
    }

    def "creates gzip compressed tar file"() {
        expect:
        final TestFile tarFile = initializeTarFile(temporaryFolder.testDirectory.file("test.tgz"), GzipArchiver.getCompressor())
        tarAndUntarAndCheckFileContents(tarFile)
    }

    def "creates bzip compressed tar file"() {
        expect:
        final TestFile tarFile = initializeTarFile(temporaryFolder.testDirectory.file("test.tbz2"), Bzip2Archiver.getCompressor());
        tarAndUntarAndCheckFileContents(tarFile);
    }

    private void tarAndUntarAndCheckFileContents(TestFile tarFile) {
        tar(file("dir/file1"), file("file2"));

        TestFile expandDir = temporaryFolder.getTestDirectory().file("expanded");
        tarFile.untarTo(expandDir);
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"));
        expandDir.file("file2").assertContents(equalTo("contents of file2"));
    }

    def "tar file contains expected permissions"() {
        when:
        TestFile tarFile = initializeTarFile(temporaryFolder.getTestDirectory().file("test.tar"), new SimpleCompressor());

        tar(dir("dir"), file("file"));

        then:
        def tarFixture = new TarTestFixture(tarFile)
        tarFixture.assertFileMode("dir/", 2)
        tarFixture.assertFileMode("file", 1)
    }

    def "wraps failure to open output file"() {
        when:
        TestFile tarFile = initializeTarFile(temporaryFolder.createDir("test.tar"), new SimpleCompressor());

        action.execute(new CopyActionProcessingStream() {
            public void process(CopyActionProcessingStreamAction action) {
                // nothing
            }
        });

        then:
        def e = thrown(GradleException)
        e.message == "Could not create TAR '${tarFile}'." as String
    }

    def "wraps failure to add element"() {
        when:
        TestFile tarFile = initializeTarFile(temporaryFolder.getTestDirectory().file("test.tar"), new SimpleCompressor());

        Throwable failure = new RuntimeException("broken");
        visit(action, brokenFile("dir/file1", failure));

        then:
        def e = thrown(GradleException)
        e.message == "Could not add [dir/file1] to TAR '${tarFile}'." as String
        e.cause == failure
    }

    private TestFile initializeTarFile(final TestFile tarFile, final ArchiveOutputStreamFactory compressor) {
        action = new TarCopyAction(tarFile, compressor, false);
        return tarFile;
    }

    private void tar(final FileCopyDetailsInternal... files) {
        action.execute(new CopyActionProcessingStream() {
            public void process(CopyActionProcessingStreamAction action) {
                for (FileCopyDetailsInternal f : files) {
                    action.processFile(f);
                }
            }
        });
    }

    private FileCopyDetailsInternal file(final String path) {
        final String content = String.format("contents of %s", path);

        final FileCopyDetailsInternal details = Stub(FileCopyDetailsInternal)
        details.getRelativePath() >> RelativePath.parse(true, path)
        details.getLastModified() >> 1000L
        details.getSize() >> content.getBytes().length
        details.isDirectory() >> false
        details.getMode() >> 1
        details.copyTo(_ as OutputStream) >> {OutputStream out -> IOUtils.write(content, out);}

        return details;
    }

    private FileCopyDetailsInternal dir(final String path) {

        final FileCopyDetailsInternal details = Stub(FileCopyDetailsInternal)
        details.getRelativePath() >> RelativePath.parse(false, path)
        details.getLastModified() >> 1000L
        details.isDirectory() >> true
        details.getMode() >> 2

        return details;
    }

    private FileCopyDetailsInternal brokenFile(final String path, final Throwable failure) {

        final FileCopyDetailsInternal details = Stub(FileCopyDetailsInternal)
        details.getRelativePath() >> RelativePath.parse(true, path)
        details.getLastModified() >> 1000L
        details.getSize() >> 1000L
        details.isDirectory() >> false
        details.getMode() >> 1
        details.toString() >> "[dir/file1]"
        details.copyTo(_ as OutputStream) >> {OutputStream out -> throw failure }

        return details;
    }
}
