package org.gradle.api.internal.file

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.util.OperatingSystem
import org.gradle.util.PosixUtil

class FileCanonicalisationTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()

    def "normalises absolute path which points to an absolute link"() {
        def target = createFile(new File(tmpDir.dir, 'target.txt'))
        def file = new File(tmpDir.dir, 'a/other.txt')
        link(target, file)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which points to a relative link"() {
        createFile(new File(tmpDir.dir, 'target.txt'))
        def file = new File(tmpDir.dir, 'a/other.txt')
        link('../target.txt', file)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which has mismatched case"() {
        if (OperatingSystem.current().caseSensitiveFileSystem) {
            return
        }
        def file = createFile(new File(tmpDir.dir, 'dir/file.txt'))
        def path = new File(tmpDir.dir, 'dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises absolute path which points to a link using mismatched case"() {
        if (OperatingSystem.current().caseSensitiveFileSystem) {
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
        def file = new File(tmpDir.dir, 'a/other.txt')
        link('unknown.txt', file)
        assert !file.exists() && !file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor is an absolute link"() {
        def target = createFile(new File(tmpDir.dir, 'target/file.txt'))
        def file = new File(tmpDir.dir, 'a/b/file.txt')
        link(target.parentFile, file.parentFile)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor has mismatched case"() {
        def file = createFile(new File(tmpDir.dir, "a/b/file.txt"))
        def path = new File(tmpDir.dir, "A/b/file.txt")
        assert file.exists() && file.file

        expect:
        normalise(path) == file
    }

    def "normalises ancestor with mismatched case when target file does not exist"() {
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
        expect: false
    }

    def "normalises path which uses windows 8.3 name"() {
        expect: false
    }

    def "normalises file system roots"() {
        expect: false
    }

    def "normalises non-existent file system root"() {
        expect: false
    }

    def "fails when relative path refers to ancestor of file system root"() {
        expect: false
    }

    def link(File target, File file) {
        link(target.absolutePath, file)
    }

    def link(String target, File file) {
        file.getParentFile().mkdirs()
        PosixUtil.current().symlink(target, file.getAbsolutePath())
    }

    def createFile(File file) {
        file.parentFile.mkdirs()
        file.text = 'content'
        return file
    }

    def normalise(Object path, File baseDir = tmpDir.dir) {
        return new BaseDirConverter(baseDir).resolve(path)
    }
}
