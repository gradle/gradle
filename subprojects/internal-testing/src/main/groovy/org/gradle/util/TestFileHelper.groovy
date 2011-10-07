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
package org.gradle.util

import java.util.zip.ZipInputStream

import org.apache.commons.lang.StringUtils
import org.apache.tools.ant.taskdefs.Expand
import org.apache.tools.ant.taskdefs.Untar
import org.gradle.api.UncheckedIOException
import org.gradle.os.OperatingSystem

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class TestFileHelper {
    TestFile file

    TestFileHelper(TestFile file) {
        this.file = file
    }

    void unzipTo(File target, boolean nativeTools) {
        // Check that each directory in hierarchy is present
        file.withInputStream {InputStream instr ->
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

        if (nativeTools && OperatingSystem.current().isUnix()) {
            def process = ['unzip', '-o', file.absolutePath, '-d', target.absolutePath].execute()
            process.consumeProcessOutput(System.out, System.err)
            assertThat(process.waitFor(), equalTo(0))
            return
        }

        def unzip = new Expand()
        unzip.src = file
        unzip.dest = target
        AntUtil.execute(unzip)
    }

    void untarTo(File target, boolean nativeTools) {
        if (nativeTools && OperatingSystem.current().isUnix()) {
            target.mkdirs()
            def builder = new ProcessBuilder(['tar', '-xf', file.absolutePath])
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

        AntUtil.execute(untar)
    }

    String getPermissions() {
        def stat = org.gradle.os.PosixUtil.current().stat(file.absolutePath)
        [6, 3, 0].collect {
            def m = stat.mode() >> it
            [m & 4 ? 'r' : '-', m & 2 ? 'w' : '-', m & 1 ? 'x' : '-']
        }.flatten().join('')
    }

    void setPermissions(String permissions) {
        def m = [6, 3, 0].inject(0) { mode, pos ->
            mode |= permissions[9 - pos - 3] == 'r' ? 4 << pos : 0
            mode |= permissions[9 - pos - 2] == 'w' ? 2 << pos : 0
            mode |= permissions[9 - pos - 1] == 'x' ? 1 << pos : 0
            return mode
        }
        def retval = org.gradle.os.PosixUtil.current().chmod(file.absolutePath, m)
        if (retval != 0) {
            throw new UncheckedIOException("Could not set permissions of '${file}' to '${permissions}'.")
        }
    }
}