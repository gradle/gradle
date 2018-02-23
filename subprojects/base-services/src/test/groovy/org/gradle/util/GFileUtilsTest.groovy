/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.UncheckedIOException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.GFileUtils.*

class GFileUtilsTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp

    def "can read the file's tail"() {
        def f = temp.file("foo.txt") << """
one
two
three
"""
        when:
        def out = GFileUtils.tail(f, 2)

        then:
        out == """two
three
"""
    }

    def "mkdirs succeeds if directory already exists"() {
        def dir = temp.createDir("foo")
        assert dir.exists()

        when:
        GFileUtils.mkdirs(dir)

        then:
        noExceptionThrown()
        dir.exists()
    }

    def "can mkdirs"() {
        given:
        def f = temp.file("a/b/c/d")

        expect:
        !f.isDirectory()

        when:
        mkdirs(f)

        then:
        f.isDirectory()
    }

    def "can parentMkdirs"() {
        given:
        def f = temp.file("a/b/c/d")

        expect:
        !f.parentFile.exists()

        when:
        def p = parentMkdirs(f)

        then:
        p.isDirectory()
        f.parentFile == p
    }

    def "mkdirs fails if can't make parent"() {
        given:
        def e = temp.file("a/b/c/d/e")
        def b = temp.createFile("a/b")
        def c = temp.file("a/b/c")

        expect:
        b.file

        when:
        mkdirs(e)

        then:
        def ex = thrown UncheckedIOException
        ex.message == "Cannot create parent directory '$c' when creating directory '$e' as '$b' is not a directory"
    }

    def "reads file quietly"() {
        temp.file("foo.txt") << "hey"

        expect:
        readFileQuietly(temp.file("foo.txt")) == "hey"
        readFileQuietly(new File("missing")) == "Unable to read file 'missing' due to: org.gradle.api.UncheckedIOException: java.io.FileNotFoundException: File 'missing' does not exist"
        readFileQuietly(temp.createDir("dir")).startsWith "Unable to read file"
    }

    def "touch creates new empty file"() {
        def foo = temp.file("foo.txt")

        when:
        touch(foo)
        then:
        foo.exists()
        foo.length() == 0
        foo.file
    }

    def "touch touches existing file"() {
        def foo = temp.file("foo.txt") << "data"
        def original = foo.makeOlder().lastModified()

        when:
        touch(foo)
        then:
        foo.file
        foo.text == "data"
        foo.lastModified() > original
    }

    def "touch touches existing directory"() {
        def foo = temp.file("foo").createDir()
        def child = foo.file("data.txt") << "data"
        def original = foo.makeOlder().lastModified()

        when:
        touch(foo)
        then:
        foo.directory
        foo.lastModified() > original
        child.text == "data"
    }
}
