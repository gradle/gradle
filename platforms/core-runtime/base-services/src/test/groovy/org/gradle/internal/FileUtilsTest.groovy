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

package org.gradle.internal


import spock.lang.Specification

import static org.gradle.internal.FileUtils.calculateRoots
import static org.gradle.internal.FileUtils.withExtension

class FileUtilsTest extends Specification {

    private static final String SEP = File.separator

    List<File> toRoots(Iterable<? extends File> files) {
        calculateRoots(files)
    }

    List<File> files(String... paths) {
        paths.collect { new File("/", it).absoluteFile }
    }

    def "can find roots when leafs are directories"() {
        expect:
        toRoots([]) == []
        toRoots(files("a/a", "a/a")) == files("a/a")
        toRoots(files("a", "b", "c")) == files("a", "b", "c")
        toRoots(files("a/a", "a/a/a", "a/b/a")) == files("a/a", "a/b/a")
        toRoots(files("a/a/a", "a/a", "a/b/a")) == files("a/a", "a/b/a")
        toRoots(files("a/a", "a/a-1", "a/a/a")) == files("a/a", "a/a-1")
        toRoots(files("a/a", "a/a/a", "b/a/a")) == files("a/a", "b/a/a")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b")) == files("a/a/a/a/a/a/a/a/a", "a/b")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a")) == files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a")
        toRoots(files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a/a/a/a/a/a/a/a", "b/a/a/a/a")) == files("a/a/a/a/a/a/a/a/a", "a/b", "b/a/a/a/a")
    }

    def "can transform filenames to alternate extensions"() {
        expect:
        withExtension("foo", ".bar") == "foo.bar"
        withExtension("/some/path/to/foo", ".bar") == "/some/path/to/foo.bar"
        withExtension("foo.baz", ".bar") == "foo.bar"
        withExtension("/some/path/to/foo.baz", ".bar") == "/some/path/to/foo.bar"
        withExtension("\\some\\path\\to\\foo.baz", ".bar") == "\\some\\path\\to\\foo.bar"
        withExtension("/some/path/to/foo.boo.baz", ".bar") == "/some/path/to/foo.boo.bar"
        withExtension("/some/path/to/foo.bar", ".bar") == "/some/path/to/foo.bar"
    }

    def "can determine if one path start with another"(String path, String startsWithPath, boolean result) {
        expect:
        FileUtils.doesPathStartWith(path, startsWithPath) == result

        where:
        path              | startsWithPath || result
        ""                | ""             || true
        "a${SEP}a${SEP}a" | "a${SEP}b"     || false
        "a${SEP}a"        | "a${SEP}a"     || true
        "a${SEP}a${SEP}a" | "a${SEP}a"     || true
        "a${SEP}ab"       | "a${SEP}a"     || false
    }

    def "can add suffix to filename"() {
        expect:
        FileUtils.addSuffixToName(original, suffix) == result

        where:
        original            | suffix     | result
        "file.zip"          | "-1"       | "file-1.zip"
        "file.tar.gz"       | "-bla-bla" | "file-bla-bla.tar.gz"
        "file.with.dots.gz" | "-2"       | "file-2.with.dots.gz"
        "file"              | "-1"       | "file-1"
        "file"              | ""         | "file"
        "file."             | "-2"       | "file-2."
    }
}
