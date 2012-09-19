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

import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.GFileUtils.mkdirs
import static org.gradle.util.GFileUtils.parentMkdirs
import org.gradle.api.UncheckedIOException

/**
 * by Szczepan Faber, created at: 2/28/12
 */
class GFileUtilsTest extends Specification {

    @Rule TemporaryFolder temp

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

    def "createDirectory() succeeds if directory already exists"() {
        def dir = temp.createDir("foo")
        assert dir.exists()

        when:
        GFileUtils.createDirectory(dir)

        then:
        noExceptionThrown()
        dir.exists()
    }

    def "relative path"() {
        when:
        def from = new File(fromPath)
        def to = new File(toPath)

        then:
        GFileUtils.relativePath(from, to) == path

        where:
        fromPath | toPath  | path
        "a"      | "a/b"   | "b"
        "a"      | "a/b/a" | "b/a"
        "a"      | "b"     | "../b"
        "a/b"    | "b"     | "../../b"
        "a"      | "a"     | ""
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

}
