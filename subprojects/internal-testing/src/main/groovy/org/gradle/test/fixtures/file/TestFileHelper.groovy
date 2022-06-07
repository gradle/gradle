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
import groovy.io.FileType
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.Expand
import org.apache.tools.ant.taskdefs.Untar

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Function
import java.util.zip.ZipInputStream

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class TestFileHelper {
    TestFile file

    TestFileHelper(TestFile file) {
        this.file = file
    }

    void unzipTo(File target, boolean nativeTools) {
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
                def parent = StringUtils.substringBeforeLast(entry.name, '/') + '/'
                assertTrue("Missing dir '$parent'", dirs.contains(parent))
            }
        }

        if (nativeTools && isUnix()) {
            def process = ['unzip', '-q', '-o', file.absolutePath, '-d', target.absolutePath].execute()
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
            return
        }

        def unzip = new Expand()
        unzip.src = file
        unzip.dest = target

        unzip.project = new Project()
        unzip.execute()
    }

    void untarTo(File target, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            target.mkdirs()
            def builder = new ProcessBuilder(['tar', '-xpf', file.absolutePath])
            builder.directory(target)
            def process = builder.start()
            process.consumeProcessOutput()
            assertThat(process.waitFor(), equalTo(0))
            return
        }

        def untar = new Untar()
        untar.setSrc(file)
        untar.setDest(target)

        if (file.name.endsWith(".tgz")) {
            def method = new Untar.UntarCompressionMethod()
            method.value = "gzip"
            untar.compression = method
        } else if (file.name.endsWith(".tbz2")) {
            def method = new Untar.UntarCompressionMethod()
            method.value = "bzip2"
            untar.compression = method
        }

        untar.project = new Project()
        untar.execute()
    }

    private boolean isUnix() {
        return !System.getProperty('os.name').toLowerCase().contains('windows')
    }

    String getPermissions() {
        if (!isUnix()) {
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
        if (!isUnix()) {
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

    private int toMode(String permissions) {
        int m = [6, 3, 0].inject(0) { mode, pos ->
            mode |= permissions[9 - pos - 3] == 'r' ? 4 << pos : 0
            mode |= permissions[9 - pos - 2] == 'w' ? 2 << pos : 0
            mode |= permissions[9 - pos - 1] == 'x' ? 1 << pos : 0
            return mode
        }
        return m
    }

    int getMode() {
        return toMode(getPermissions())
    }

    void delete(boolean nativeTools) {
        if (isUnix() && nativeTools) {
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
        def process = ["readlink", file.absolutePath].execute()
        def error = process.errorStream.text
        def retval = process.waitFor()
        if (retval != 0) {
            throw new RuntimeException("Could not read link '$file': $error")
        }
        return process.inputStream.text.trim()
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

    void zipTo(TestFile zipFile, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            def process = ['zip', zipFile.absolutePath, "-r", file.name].execute(null, file.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
        } else {
            archiveTo(zipFile, ZipArchiveOutputStream::new)
        }
    }

    void tarTo(TestFile tarFile, boolean nativeTools) {
        if (nativeTools && isUnix()) {
            def process = ['tar', "-cf", tarFile.absolutePath, file.name].execute(null, file.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
        } else {
            archiveTo(tarFile, TarArchiveOutputStream::new)
        }
    }

    void tgzTo(TestFile archiveFile) {
        archiveTo(archiveFile, os -> new TarArchiveOutputStream(new GzipCompressorOutputStream(os)))
    }

    void tbzTo(TestFile archiveFile) {
        archiveTo(archiveFile, os -> new TarArchiveOutputStream(new BZip2CompressorOutputStream(os)))
    }

    void bzip2To(TestFile archiveFile) {
        archiveTo(archiveFile, BZip2CompressorOutputStream::new)
    }

    void gzipTo(TestFile archiveFile) {
        archiveTo(archiveFile, GzipCompressorOutputStream::new)
    }

    private void archiveTo(TestFile archiveFile, Function<OutputStream, ArchiveOutputStream> compressor) {
        def filesToArchive = getFilesToArchive()
        try (OutputStream fo = Files.newOutputStream(archiveFile.toPath())
             ArchiveOutputStream o = compressor.apply(fo)) {
            for (File f : filesToArchive) {
                ArchiveEntry entry = o.createArchiveEntry(f, entryName(f))
                o.putArchiveEntry(entry)
                if (f.isFile()) {
                    try (InputStream i = Files.newInputStream(f.toPath())) {
                        IOUtils.copy(i, o)
                    }
                }
                o.closeArchiveEntry()
            }
        }
    }

    private Collection<File> getFilesToArchive() {
        def list = []
        file.eachFileRecurse (FileType.ANY) { f ->
            list << f
        }
        return list
    }

    private String entryName(File f) {
        file.toPath().relativize(f.toPath()).toString()
    }

}