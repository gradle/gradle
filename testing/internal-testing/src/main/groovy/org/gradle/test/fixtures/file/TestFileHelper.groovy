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
package org.gradle.test.fixtures.file

import com.google.common.io.ByteStreams
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class TestFileHelper {
    TestFile file

    TestFileHelper(TestFile file) {
        this.file = file
    }

    void unzipTo(File target, boolean nativeTools, boolean checkParentDirs = true) {
        // Check that each directory in hierarchy is present
        file.withInputStream { InputStream instr ->
            def dirs = [] as Set
            def zipStr = new ZipInputStream(instr)
            def entry
            while (entry = zipStr.getNextEntry()) {
                if (entry.directory) {
                    assertTrue("Duplicate directory '$entry.name'", dirs.add(entry.name))
                }
                if (!entry.name.contains('/')) {
                    continue
                }
                if (checkParentDirs) {
                    def parent = StringUtils.substringBeforeLast(entry.name, '/') + '/'
                    assertTrue("Missing dir '$parent'", dirs.contains(parent))
                }
            }
        }

        if (nativeTools && !isWindows()) {
            def process = ['unzip', '-q', '-o', file.absolutePath, '-d', target.absolutePath].execute()
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
            return
        }

        target.mkdirs()
        file.withInputStream { InputStream instr ->
            new ZipInputStream(instr).withCloseable { ZipInputStream zipStr ->
                def entry
                while (entry = zipStr.nextEntry) {
                    def outFile = new File(target, entry.name)
                    if (entry.directory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        outFile.withOutputStream { os -> os << zipStr }
                    }
                }
            }
        }
    }

    void untarTo(File target, boolean nativeTools) {
        if (nativeTools && !isWindows()) {
            target.mkdirs()
            def builder = new ProcessBuilder(['tar', '-xpf', file.absolutePath])
            builder.directory(target)
            def process = builder.start()
            process.consumeProcessOutput()
            assertThat(process.waitFor(), equalTo(0))
            return
        }

        target.mkdirs()
        file.withInputStream { InputStream instr ->
            InputStream stream = getInputStreamForFile(instr)
            new TarArchiveInputStream(stream).withCloseable { TarArchiveInputStream tarIn ->
                TarArchiveEntry entry
                while ((entry = tarIn.nextEntry) != null) {
                    def outFile = new File(target, entry.name)
                    if (entry.directory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile.mkdirs()
                        outFile.withOutputStream { os -> os << tarIn }
                    }
                }
            }
        }
    }

    private InputStream getInputStreamForFile(InputStream instr) {
        if (file.name.endsWith(".tgz")) {
            return new GzipCompressorInputStream(instr)
        }
        if (file.name.endsWith(".tbz2")) {
            return new BZip2CompressorInputStream(instr)
        }
        return instr
    }

    String getPermissions() {
        if (isWindows()) {
            return "-rwxr-xr-x"
        }

        def process = ["ls", "-ld", file.absolutePath].execute()
        def result = process.inputStream.text
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not list permissions for '$file': $error")
        }
        def perms = result.split()[0]
        assert perms.matches("[d\\-][rwx\\-]{9}[@\\.\\+]?")
        return perms.substring(1, 10)
    }

    void setPermissions(String permissions) {
        if (isWindows()) {
            return
        }
        def perms = PosixFilePermissions.fromString(permissions)
        Files.setPosixFilePermissions(file.toPath(), perms)
    }

    void setMode(int mode) {
        // TODO: Remove this entirely and use built-in Files.setPosixFilePermissions
        def process = ["chmod", Integer.toOctalString(mode), file.absolutePath].execute()
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not set permissions for '$file': $error")
        }
    }

    int getMode() {
        return toMode(getPermissions())
    }

    void delete(boolean nativeTools) {
        if (!isWindows() && nativeTools) {
            def process = ["rm", "-rf", file.absolutePath].execute()
            def error = process.errorStream.text
            def retval = process.waitFor()
            if (retval != 0) {
                throw new RuntimeException("Could not delete '$file': $error")
            }
        } else {
            FileUtils.deleteQuietly(file)
        }
    }

    String readLink() {
        Files.readSymbolicLink(file.toPath()).toFile().absolutePath
    }

    ExecOutput exec(List args) {
        return execute(args, null)
    }

    ExecOutput execute(List args, List env) {
        def process = ([file.absolutePath] + args).execute(env, null)

        // Prevent process from hanging by consuming the output as we go.
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()

        Thread outputThread = Thread.start { ByteStreams.copy(process.in, output) }
        Thread errorThread = Thread.start { ByteStreams.copy(process.err, error) }

        int exitCode = process.waitFor()
        outputThread.join()
        errorThread.join()

        return new ExecOutput(exitCode, output.toString(), error.toString())
    }

    ExecOutput executeSuccess(List args, List env) {
        def result = execute(args, env)
        if (result.exitCode != 0) {
            throw new RuntimeException("Could not execute $file. Error: ${result.error}, Output: ${result.out}")
        }
        return result
    }

    ExecOutput executeFailure(List args, List env) {
        def result = execute(args, env)
        if (result.exitCode == 0) {
            throw new RuntimeException("Unexpected success, executing $file. Error: ${result.error}, Output: ${result.out}")
        }
        return result
    }

    void zipTo(TestFile zipFile, boolean nativeTools, boolean readOnly) {
        if (nativeTools && !isWindows()) {
            def process = ['zip', zipFile.absolutePath, "-r", file.name].execute(null, file.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
        } else {
            zipFile.withOutputStream { os ->
                new ZipArchiveOutputStream(os).withCloseable { zos ->
                    addDirContentsToZip(zos, file, "", readOnly)
                }
            }
        }
    }

    void tarTo(TestFile tarFile, boolean nativeTools, boolean readOnly) {
        //TODO: there is an inconsistency here; when using native tools the root folder is put into the TAR, but only its content is packaged by the other branch
        // for example if we put an empty folder into the TAR, then native tools will insert an entry with an empty directory, while the other branch will insert no entries
        if (nativeTools && !isWindows()) {
            def process = ['tar', "-cf", tarFile.absolutePath, file.name].execute(null, file.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
        } else {
            tarFile.withOutputStream { os ->
                writeTar(os, readOnly)
            }
        }
    }

    void tgzTo(TestFile tarFile, boolean readOnly) {
        tarFile.withOutputStream { os ->
            new GzipCompressorOutputStream(os).withCloseable { gz ->
                writeTar(gz, readOnly)
            }
        }
    }

    void tbzTo(TestFile tarFile, boolean readOnly) {
        tarFile.withOutputStream { os ->
            new BZip2CompressorOutputStream(os).withCloseable { bz ->
                writeTar(bz, readOnly)
            }
        }
    }

    void bzip2To(TestFile compressedFile) {
        compressedFile.withOutputStream { os ->
            new BZip2CompressorOutputStream(os).withCloseable { bz ->
                file.withInputStream { is -> bz << is }
            }
        }
    }

    void gzipTo(TestFile compressedFile) {
        def outStr = new GZIPOutputStream(new FileOutputStream(compressedFile))
        try {
            outStr.bytes = file.bytes
        } finally {
            outStr.close()
        }
    }

    private void writeTar(OutputStream out, boolean readOnly) {
        new TarArchiveOutputStream(out).withCloseable { TarArchiveOutputStream tos ->
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            addDirContentsToTar(tos, file, "", readOnly)
        }
    }

    private static void addDirContentsToZip(ZipArchiveOutputStream zos, File dir, String prefix, boolean readOnly) {
        File[] children = dir.listFiles()
        if (children == null) {
            return
        }
        Arrays.sort(children)
        for (File child : children) {
            String name = prefix + child.name
            if (child.isDirectory()) {
                String dirName = name + "/"
                ZipArchiveEntry entry = new ZipArchiveEntry(child, dirName)
                if (readOnly) {
                    entry.setUnixMode(0555)
                }
                zos.putArchiveEntry(entry)
                zos.closeArchiveEntry()
                addDirContentsToZip(zos, child, dirName, readOnly)
            } else {
                ZipArchiveEntry entry = new ZipArchiveEntry(child, name)
                if (readOnly) {
                    entry.setUnixMode(0444)
                }
                zos.putArchiveEntry(entry)
                child.withInputStream { is -> zos << is }
                zos.closeArchiveEntry()
            }
        }
    }

    private static void addDirContentsToTar(TarArchiveOutputStream tos, File dir, String prefix, boolean readOnly) {
        File[] children = dir.listFiles()
        if (children == null) {
            return
        }
        Arrays.sort(children)
        for (File child : children) {
            String name = prefix + child.name
            if (child.isDirectory()) {
                String dirName = name + "/"
                TarArchiveEntry entry = new TarArchiveEntry(child, dirName)
                if (readOnly) {
                    entry.setMode(0555)
                }
                tos.putArchiveEntry(entry)
                tos.closeArchiveEntry()
                addDirContentsToTar(tos, child, dirName, readOnly)
            } else {
                TarArchiveEntry entry = new TarArchiveEntry(child, name)
                if (readOnly) {
                    entry.setMode(0444)
                }
                tos.putArchiveEntry(entry)
                child.withInputStream { is -> tos << is }
                tos.closeArchiveEntry()
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty('os.name').toLowerCase().contains('windows')
    }

    private static int toMode(String permissions) {
        int m = [6, 3, 0].inject(0) { mode, pos ->
            mode |= permissions[9 - pos - 3] == 'r' ? 4 << pos : 0
            mode |= permissions[9 - pos - 2] == 'w' ? 2 << pos : 0
            mode |= permissions[9 - pos - 1] == 'x' ? 1 << pos : 0
            return mode
        }
        return m
    }
}
