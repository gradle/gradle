/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file

import org.gradle.util.OperatingSystem
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.os.PosixUtil

class FileCanonicalisationTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final def fileSystem = OperatingSystem.current().fileSystem

    def "normalises absolute path which points to an absolute link"() {
        if (!fileSystem.symlinkAware) {
            return
        }
        def target = createFile(new File(tmpDir.dir, 'target.txt'))
        def file = new File(tmpDir.dir, 'a/other.txt')
        link(target, file)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which points to a relative link"() {
        if (!fileSystem.symlinkAware) {
            return
        }
        createFile(new File(tmpDir.dir, 'target.txt'))
        def file = new File(tmpDir.dir, 'a/other.txt')
        link('../target.txt', file)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which has mismatched case"() {
        if (fileSystem.caseSensitive) {
            return
        }
        def file = createFile(new File(tmpDir.dir, 'dir/file.txt'))
        def path = new File(tmpDir.dir, 'dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises absolute path which points to a link using mismatched case"() {
        if (fileSystem.caseSensitive || !fileSystem.symlinkAware) {
            return
        }
        def target = createFile(new File(tmpDir.dir, 'target.txt'))
        def file = new File(tmpDir.dir, 'dir/file.txt')
        link(target, file)
        def path = new File(tmpDir.dir, 'dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises path which points to a link to something that does not exist"() {
        if (!fileSystem.symlinkAware) {
            return
        }
        def file = new File(tmpDir.dir, 'a/other.txt')
        link('unknown.txt', file)
        assert !file.exists() && !file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor is an absolute link"() {
        if (!fileSystem.symlinkAware) {
            return
        }
        def target = createFile(new File(tmpDir.dir, 'target/file.txt'))
        def file = new File(tmpDir.dir, 'a/b/file.txt')
        link(target.parentFile, file.parentFile)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor has mismatched case"() {
        if (fileSystem.caseSensitive) {
            return
        }
        def file = createFile(new File(tmpDir.dir, "a/b/file.txt"))
        def path = new File(tmpDir.dir, "A/b/file.txt")
        assert file.exists() && file.file

        expect:
        normalise(path) == file
    }

    def "normalises ancestor with mismatched case when target file does not exist"() {
        if (fileSystem.caseSensitive) {
            return
        }
        tmpDir.createDir("a")
        def file = new File(tmpDir.dir, "a/b/file.txt")
        def path = new File(tmpDir.dir, "A/b/file.txt")

        expect:
        normalise(path) == file
    }

    def "normalises relative path"() {
        def ancestor = new File(tmpDir.dir, "test")
        def baseDir = new File(ancestor, "base")
        def sibling = new File(ancestor, "sub")
        def child = createFile(new File(baseDir, "a/b/file.txt"))

        expect:
        normalise("a/b/file.txt", baseDir) == child
        normalise("./a/b/file.txt", baseDir) == child
        normalise(".//a/b//file.txt", baseDir) == child
        normalise("sub/../a/b/file.txt", baseDir) == child
        normalise("../sub", baseDir) == sibling
        normalise("..", baseDir) == ancestor
        normalise(".", baseDir) == baseDir
    }

    def "normalises relative path when base dir is a link"() {
        if (!fileSystem.symlinkAware) {
            return
        }
        def target = createFile(new File(tmpDir.dir, 'target/file.txt'))
        def baseDir = new File(tmpDir.dir, 'base')
        link("target", baseDir)
        def file = new File(baseDir, 'file.txt')
        assert file.exists() && file.file

        expect:
        normalise('file.txt', baseDir) == file
    }

    def "normalises path which uses windows 8.3 name"() {
        if (!OperatingSystem.current().windows) {
            return
        }
        def file = createFile(new File(tmpDir.dir, 'dir/file-with-long-name.txt'))
        def path = new File(tmpDir.dir, 'dir/FILE-W~1.TXT')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises file system roots"() {
        expect:
        normalise(root) == root

        where:
        root << File.listRoots()
    }

    def "normalises non-existent file system root"() {
        if (!OperatingSystem.current().windows) {
            return
        }
        def file = new File("Q:\\")
        assert !file.exists()
        assert file.absolute

        expect:
        normalise(file) == file
    }

    def "normalises relative path that refers to ancestor of file system root"() {
        File root = File.listRoots()[0]

        expect:
        normalise("../../..", root) == root
    }

    def link(File target, File file) {
        link(target.absolutePath, file)
    }

    def link(String target, File file) {
        file.getParentFile().mkdirs()

        def posix = PosixUtil.current()
        int retval = posix.symlink(target, file.getAbsolutePath())
        if (retval != 0) {
            throw new IOException("Could not create link ${file} to ${target}. errno = ${posix.errno()}")
        }
    }

    def createFile(File file) {
        file.parentFile.mkdirs()
        file.text = 'content'
        return file
    }

    def normalise(Object path, File baseDir = tmpDir.dir) {
        return new BaseDirFileResolver(baseDir).resolve(path)
    }
}
