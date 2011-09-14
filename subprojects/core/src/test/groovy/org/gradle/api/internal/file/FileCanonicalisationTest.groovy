package org.gradle.api.internal.file

import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class FileCanonicalisationTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()

    def "normalises absolute path which points to an absolute link"() {
        def target = tmpDir.createFile('target.txt')
        def file = tmpDir.file('a/other.txt')
        file.linkTo(target)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which points to a relative link"() {
        tmpDir.createFile('target.txt')
        def file = tmpDir.file('a/other.txt')
        file.linkTo('../target.txt')
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises absolute path which has mismatched case"() {
        def file = tmpDir.createFile('dir/file.txt')
        def path = tmpDir.file('dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises absolute path which points to a link using mismatched case"() {
        def target = tmpDir.createFile('target.txt')
        def file = tmpDir.file('dir/file.txt')
        file.linkTo(target)
        def path = tmpDir.file('dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalise(path) == file
    }

    def "normalises path which points to a link to something that does not exist"() {
        def file = tmpDir.file('a/other.txt')
        file.linkTo('unknown.txt')
        assert !file.exists() && !file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor is an absolute link"() {
        def target = tmpDir.createFile('target/file.txt')
        def file = tmpDir.file('a/b/file.txt')
        file.parentFile.linkTo(target.parentFile)
        assert file.exists() && file.file

        expect:
        normalise(file) == file
    }

    def "normalises path when ancestor has mismatched case"() {
        expect: false
    }

    def "normalises ancestor when target file does not exist"() {
        expect: false
    }

    def "normalises relative path"() {
        expect: false
    }

    def "normalises relative path when base dir is a link"() {
        expect: false
    }

    def "normalises path which uses windows 8.3 name"() {
        expect: false
    }

    def normalise(Object path) {
        if (path instanceof File) {
            def result = path.canonicalFile
            assert result == normalise(path.absolutePath)
            assert result == normalise(path.path)
            return result
        }

        return new File(path.toString()).canonicalFile
    }
}
