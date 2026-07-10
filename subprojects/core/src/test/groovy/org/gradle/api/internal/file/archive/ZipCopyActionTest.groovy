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

import org.apache.commons.compress.archivers.zip.Zip64RequiredException
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.DefaultFilePermissions
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.DefaultZipCompressor
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.tasks.bundling.Zip
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit
import static org.hamcrest.CoreMatchers.equalTo

class ZipCopyActionTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    ZipCopyAction visitor
    TestFile zipFile
    def encoding = 'UTF-8'

    def setup() {
        zipFile = tmpDir.getTestDirectory().file("test.zip")
        visitor = new ZipCopyAction(zipFile, new DefaultZipCompressor(false, ZipArchiveOutputStream.STORED), new DocumentationRegistry(), encoding, false, Providers.notDefined())
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
        visitor = new ZipCopyAction(invalidZipFile, new DefaultZipCompressor(false, ZipArchiveOutputStream.STORED), new DocumentationRegistry(), encoding, false, Providers.notDefined())

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
        def zipOutputStream = Mock(ZipArchiveOutputStream)
        zipOutputStream.close() >> {
            throw new Zip64RequiredException("xyz")
        }

        def compressor = new DefaultZipCompressor(false, ZipArchiveOutputStream.STORED) {
            @Override
            ZipArchiveOutputStream createArchiveOutputStream(File destination) {
                zipOutputStream
            }
        }

        def docRegistry = Mock(DocumentationRegistry)
        1 * docRegistry.getDslRefForProperty(Zip.name, "zip64") >> "doc url"
        0 * docRegistry._

        visitor = new ZipCopyAction(zipFile, compressor, docRegistry, encoding, false, Providers.notDefined())

        when:
        zip(file("file2"))

        then:
        def e = thrown(org.gradle.api.tasks.bundling.internal.Zip64RequiredException)
        e.message == "xyz\n\nTo build this archive, please enable the zip64 extension.\nSee: doc url"
    }

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

    @Issue("https://github.com/gradle/gradle/pull/37790")
    def "zip with reproducibleFileTimestamp just after a DST transition is independent of the default time zone"() {
        given:
        // 2009-03-29T01:30:00Z is 30 minutes after the Europe/Paris DST transition at 2009-03-29T01:00:00Z,
        // so the zone offset at the timestamp (+02:00) differs from the offset at the instant stored in the zip
        def timestamp = java.time.Instant.ofEpochSecond(1238290200).toEpochMilli()
        def utcZip = tmpDir.testDirectory.file("utc.zip")
        def parisZip = tmpDir.testDirectory.file("paris.zip")

        when:
        zipInTimeZone(utcZip, timestamp, "UTC")
        zipInTimeZone(parisZip, timestamp, "Europe/Paris")

        then:
        entryLastModified(utcZip) == entryLastModified(parisZip)
        utcZip.md5Hash == parisZip.md5Hash
    }

    @Issue("https://github.com/gradle/gradle/pull/37790")
    def "zip with reproducibleFileTimestamp below the zip minimum is raised to it and independent of the default time zone"() {
        given:
        // before 1980-02-01, the earliest date storable as MS-DOS date/time
        def timestamp = java.time.Instant.parse("1979-06-01T00:00:00Z").toEpochMilli()
        def utcZip = tmpDir.testDirectory.file("utc.zip")
        def parisZip = tmpDir.testDirectory.file("paris.zip")

        when:
        zipInTimeZone(utcZip, timestamp, "UTC")
        zipInTimeZone(parisZip, timestamp, "Europe/Paris")

        then:
        entryLastModified(utcZip) == new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).timeInMillis
        entryLastModified(utcZip) == entryLastModified(parisZip)
        utcZip.md5Hash == parisZip.md5Hash
    }

    @Issue("https://github.com/gradle/gradle/pull/37790")
    def "zip with reproducibleFileTimestamp above the zip maximum fails"() {
        given:
        // above MAXIMUM_TIME_FOR_ZIP_ENTRIES; commons-compress would add an NTFS extra field with
        // a timezone-dependent value instead of storing the timestamp as MS-DOS date/time
        def timestamp = java.time.Instant.parse("2100-01-01T00:00:00Z").toEpochMilli()

        when:
        new ZipCopyAction(zipFile, new DefaultZipCompressor(false, ZipArchiveOutputStream.STORED), new DocumentationRegistry(), encoding, false, Providers.of(timestamp))

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "The reproducible file timestamp 2100-01-01T00:00:00Z is greater than the maximum supported timestamp 2097-11-01T00:00:00Z."
    }

    private FileCopyDetailsInternal plainFile(final String path) {
        // a Spock mock would also work here, but a plain coercion keeps the test runnable
        // in environments where the Mockito mock maker cannot self-attach its Java agent
        return [
            getRelativePath: { RelativePath.parse(false, path) },
            getLastModified: { 1000L },
            isDirectory: { false },
            getPermissions: { new DefaultFilePermissions(1) },
            copyTo: { OutputStream out -> out << "contents of $path" }
        ] as FileCopyDetailsInternal
    }

    private static long entryLastModified(TestFile zipFile) {
        def zip = new java.util.zip.ZipFile(zipFile)
        try {
            return zip.entries().nextElement().time
        } finally {
            zip.close()
        }
    }

    private void zipInTimeZone(TestFile zipFile, long timestamp, String timeZoneId) {
        def originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(timeZoneId))
            visitor = new ZipCopyAction(zipFile, new DefaultZipCompressor(false, ZipArchiveOutputStream.STORED), new DocumentationRegistry(), encoding, false, Providers.of(timestamp))
            zip(plainFile("file"))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
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
        mock.getPermissions() >> new DefaultFilePermissions(1)
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
        mock.getPermissions() >> new DefaultFilePermissions(2)
        mock
    }

    private FileCopyDetailsInternal brokenFile(final String path, final Throwable failure) {
        def mock = Mock(FileCopyDetailsInternal)
        mock.getRelativePath() >> RelativePath.parse(false, path)
        mock.getLastModified() >> 1000L
        mock.isDirectory() >> false
        mock.getPermissions() >> new DefaultFilePermissions(1)
        mock.copyTo(_ as OutputStream) >> { OutputStream out ->
            failure.fillInStackTrace()
            throw failure
        }
        mock
    }
}
