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
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.Expand
import org.apache.tools.ant.taskdefs.Tar
import org.apache.tools.ant.taskdefs.Untar
import org.apache.tools.ant.taskdefs.Zip
import org.apache.tools.ant.types.ArchiveFileSet
import org.apache.tools.ant.types.EnumeratedAttribute
import org.apache.tools.ant.types.ZipFileSet
import org.apache.tools.bzip2.CBZip2OutputStream

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

        def unzip = new Expand()
        unzip.src = file
        unzip.dest = target

        unzip.project = new Project()
        unzip.execute()
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

    void zipTo(TestFile zipFile, boolean nativeTools, boolean readOnly) {
        if (nativeTools && !isWindows()) {
            def process = ['zip', zipFile.absolutePath, "-r", file.name].execute(null, file.parentFile)
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
        } else {
            Zip zip = new Zip()
            zip.setProject(new Project())
            setSourceDirectory(zip, readOnly)
            zip.setDestFile(zipFile)
            def whenEmpty = new Zip.WhenEmpty()
            whenEmpty.setValue("create")
            zip.setWhenempty(whenEmpty)
            zip.execute()
        }
    }

    private void setSourceDirectory(archiveTask, boolean readOnly) {
        if (readOnly) {
            ArchiveFileSet archiveFileSet = archiveTask instanceof Zip ? new ZipFileSet() : archiveTask.createTarFileSet()
            archiveFileSet.setDir(file)
            archiveFileSet.setFileMode("0444")
            archiveFileSet.setDirMode("0555")
            archiveTask.add(archiveFileSet)
        } else {
            archiveTask.setBasedir(file)
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
            Tar tar = new Tar()
            tar.setProject(new Project())
            setSourceDirectory(tar, readOnly)
            tar.setDestFile(tarFile)
            tar.execute()
        }
    }

    void tgzTo(TestFile tarFile, boolean readOnly) {
        Tar tar = new Tar()
        tar.setProject(new Project())
        setSourceDirectory(tar, readOnly)
        tar.setDestFile(tarFile)
        tar.setCompression((Tar.TarCompressionMethod) EnumeratedAttribute.getInstance(Tar.TarCompressionMethod.class, "gzip"))
        tar.execute()
    }

    void tbzTo(TestFile tarFile, boolean readOnly) {
        Tar tar = new Tar()
        tar.setProject(new Project())
        setSourceDirectory(tar, readOnly)
        tar.setDestFile(tarFile)
        tar.setCompression((Tar.TarCompressionMethod) EnumeratedAttribute.getInstance(Tar.TarCompressionMethod.class, "bzip2"))
        tar.execute()
    }

    void bzip2To(TestFile compressedFile) {
        def outStr = new FileOutputStream(compressedFile)
        try {
            outStr.write('BZ'.getBytes("us-ascii"))
            def zipStream = new CBZip2OutputStream(outStr)
            zipStream.bytes = file.bytes
            zipStream.close()
        } finally {
            outStr.close()
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
