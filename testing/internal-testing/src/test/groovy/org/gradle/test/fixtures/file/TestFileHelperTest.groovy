/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.test.fixtures.file

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.Rule
import spock.lang.Specification

class TestFileHelperTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "zipTo and unzipTo round-trip preserves files and directories"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        src.file("sub/nested/c.txt") << "charlie"
        TestFile zipFile = temp.testDirectory.file("out.zip")

        when:
        new TestFileHelper(src).zipTo(zipFile, false, false)

        then:
        zipFile.exists()
        zipFile.length() > 0

        when:
        TestFile target = temp.testDirectory.file("target")
        new TestFileHelper(zipFile).unzipTo(target, false)

        then:
        target.file("a.txt").text == "alpha"
        target.file("sub/b.txt").text == "bravo"
        target.file("sub/nested/c.txt").text == "charlie"
    }

    def "zipTo with readOnly encodes 0444 file mode and 0555 dir mode"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        TestFile zipFile = temp.testDirectory.file("out.zip")

        when:
        new TestFileHelper(src).zipTo(zipFile, false, true)

        then:
        Map<String, Integer> modes = readZipUnixModes(zipFile)
        modes["a.txt"] == 0444
        modes["sub/b.txt"] == 0444
        modes["sub/"] == 0555
    }

    def "tarTo and untarTo round-trip preserves files and directories"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        TestFile tarFile = temp.testDirectory.file("out.tar")

        when:
        new TestFileHelper(src).tarTo(tarFile, false, false)

        then:
        tarFile.exists()

        when:
        TestFile target = temp.testDirectory.file("target")
        new TestFileHelper(tarFile).untarTo(target, false)

        then:
        target.file("a.txt").text == "alpha"
        target.file("sub/b.txt").text == "bravo"
    }

    def "tarTo with readOnly encodes 0444 file mode and 0555 dir mode"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        TestFile tarFile = temp.testDirectory.file("out.tar")

        when:
        new TestFileHelper(src).tarTo(tarFile, false, true)

        then:
        Map<String, Integer> modes = readTarModes(tarFile, null)
        modes["a.txt"] == 0444
        modes["sub/b.txt"] == 0444
        modes["sub/"] == 0555
    }

    def "tgzTo produces gzip-compressed tar that untarTo can extract"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        TestFile tgzFile = temp.testDirectory.file("out.tgz")

        when:
        new TestFileHelper(src).tgzTo(tgzFile, false)

        then:
        tgzFile.exists()

        when:
        TestFile target = temp.testDirectory.file("target")
        new TestFileHelper(tgzFile).untarTo(target, false)

        then:
        target.file("a.txt").text == "alpha"
        target.file("sub/b.txt").text == "bravo"
    }

    def "tbzTo produces bzip2-compressed tar that untarTo can extract"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        src.file("sub/b.txt") << "bravo"
        TestFile tbz2File = temp.testDirectory.file("out.tbz2")

        when:
        new TestFileHelper(src).tbzTo(tbz2File, false)

        then:
        tbz2File.exists()

        when:
        TestFile target = temp.testDirectory.file("target")
        new TestFileHelper(tbz2File).untarTo(target, false)

        then:
        target.file("a.txt").text == "alpha"
        target.file("sub/b.txt").text == "bravo"
    }

    def "archive-creation methods create parent directories that don't yet exist"() {
        given:
        TestFile src = temp.testDirectory.file("src")
        src.file("a.txt") << "alpha"
        TestFile input = temp.testDirectory.file("input.txt")
        input << "payload"

        when:
        new TestFileHelper(src).zipTo(temp.testDirectory.file("nested/zip/out.zip"), false, false)
        new TestFileHelper(src).tarTo(temp.testDirectory.file("nested/tar/out.tar"), false, false)
        new TestFileHelper(src).tgzTo(temp.testDirectory.file("nested/tgz/out.tgz"), false)
        new TestFileHelper(src).tbzTo(temp.testDirectory.file("nested/tbz/out.tbz2"), false)
        new TestFileHelper(input).bzip2To(temp.testDirectory.file("nested/bz2/out.txt.bz2"))

        then:
        temp.testDirectory.file("nested/zip/out.zip").exists()
        temp.testDirectory.file("nested/tar/out.tar").exists()
        temp.testDirectory.file("nested/tgz/out.tgz").exists()
        temp.testDirectory.file("nested/tbz/out.tbz2").exists()
        temp.testDirectory.file("nested/bz2/out.txt.bz2").exists()
    }

    def "bzip2To compresses a single file that can be decompressed back to the original bytes"() {
        given:
        TestFile input = temp.testDirectory.file("input.txt")
        String payload = "the quick brown fox jumps over the lazy dog\n" * 50
        input << payload
        TestFile bz2File = temp.testDirectory.file("input.txt.bz2")

        when:
        new TestFileHelper(input).bzip2To(bz2File)

        then:
        bz2File.exists()
        bz2File.length() > 0

        when:
        String decompressed = bz2File.withInputStream { is ->
            new BZip2CompressorInputStream(is).withCloseable { bz ->
                bz.readAllBytes()
            }
        }.with { new String(it) }

        then:
        decompressed == payload
    }

    def "unzipTo rejects zip entries that escape the target directory (zip slip)"() {
        given:
        TestFile zipFile = temp.testDirectory.file("evil.zip")
        zipFile.withOutputStream { os ->
            new ZipArchiveOutputStream(os).withCloseable { zos ->
                def entry = new ZipArchiveEntry("../escaped.txt")
                zos.putArchiveEntry(entry)
                zos.write("pwned".bytes)
                zos.closeArchiveEntry()
            }
        }

        when:
        new TestFileHelper(zipFile).unzipTo(temp.testDirectory.file("target"), false, false)

        then:
        def e = thrown(IOException)
        e.message.contains("resolves outside")
    }

    def "untarTo rejects tar entries that escape the target directory (tar slip)"() {
        given:
        TestFile tarFile = temp.testDirectory.file("evil.tar")
        tarFile.withOutputStream { os ->
            new TarArchiveOutputStream(os).withCloseable { tos ->
                def entry = new TarArchiveEntry("../escaped.txt")
                entry.setSize(5)
                tos.putArchiveEntry(entry)
                tos.write("pwned".bytes)
                tos.closeArchiveEntry()
            }
        }

        when:
        new TestFileHelper(tarFile).untarTo(temp.testDirectory.file("target"), false)

        then:
        def e = thrown(IOException)
        e.message.contains("resolves outside")
    }

    private static Map<String, Integer> readZipUnixModes(File zipFile) {
        // Mask to permission bits; Ant's ZipFileSet historically encodes file-type bits as well (e.g. 0o100444),
        // while commons-compress encodes only the permission bits (0o444). The permission semantics are the same.
        Map<String, Integer> modes = [:]
        new ZipFile(zipFile).withCloseable { zf ->
            for (def entry : Collections.list(zf.entries)) {
                modes[entry.name] = entry.unixMode & 0777
            }
        }
        return modes
    }

    private static Map<String, Integer> readTarModes(File tarFile, String compression) {
        Map<String, Integer> modes = [:]
        tarFile.withInputStream { is ->
            new TarArchiveInputStream(is).withCloseable { tis ->
                def entry
                while ((entry = tis.nextEntry) != null) {
                    modes[entry.name] = entry.mode & 0777
                }
            }
        }
        return modes
    }
}
