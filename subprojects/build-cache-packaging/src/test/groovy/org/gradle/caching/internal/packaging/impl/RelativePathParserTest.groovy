/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.packaging.impl

import spock.lang.Specification

import java.util.function.Consumer

class RelativePathParserTest extends Specification {

    def "can parse sequence of relative paths"() {
        def parentHandler = Mock(Consumer)

        when:
        def parser = new RelativePathParser("tree-some/")
        then:
        parser.root

        when:
        parser.nextPath("tree-some/first/", true, parentHandler)
        then:
        0 * parentHandler.accept(_)
        then:
        parser.name == "first"
        parser.relativePath == "first"
        !parser.root

        when:
        parser.nextPath("tree-some/first/file.txt", false, parentHandler)
        then:
        0 * parentHandler.accept(_)
        then:
        parser.name == "file.txt"
        parser.relativePath == "first/file.txt"
        !parser.root

        when:
        parser.nextPath("tree-some/second/", true, parentHandler)
        then:
        1 * parentHandler.accept("first")
        then:
        parser.name == "second"
        parser.relativePath == "second"
        !parser.root

        when:
        parser.nextPath("tree-some/second/third/", true, parentHandler)
        then:
        0 * parentHandler.accept("first")
        then:
        parser.name == "third"
        parser.relativePath == "second/third"
        !parser.root

        when:
        parser.nextPath("tree-some/second/third/forth/", true, parentHandler)
        then:
        0 * parentHandler.accept("first")
        then:
        parser.name == "forth"
        parser.relativePath == "second/third/forth"
        !parser.root

        when:
        parser.nextPath("tree-some/second/third/one-file.txt", false, parentHandler)
        then:
        1 * parentHandler.accept("forth")
        then:
        parser.name == "one-file.txt"
        parser.relativePath == "second/third/one-file.txt"
        !parser.root

        when:
        parser.nextPath("tree-some/another-file.txt", false, parentHandler)
        then:
        1 * parentHandler.accept("third")
        then:
        1 * parentHandler.accept("second")
        then:
        parser.name == "another-file.txt"
        parser.relativePath == "another-file.txt"
        !parser.root

        when:
        parser.nextPath("tree-some/more-file.txt", false, parentHandler)
        then:
        0 * parentHandler.accept(_)
        then:
        parser.name == "more-file.txt"
        parser.relativePath == "more-file.txt"
        !parser.root

        when:
        parser.nextPath("tree-other/", true, parentHandler)
        then:
        def ex = thrown IllegalStateException
        ex.message == "Moved outside original root"
    }

    def "can parse with dots"() {
        def parser = new RelativePathParser("tree-reports.html.destination/")
        def parentHandler = Mock(Consumer)

        when:
        parser.nextPath("tree-reports.html.destination/classes/", true, parentHandler)
        then:
        0 * parentHandler.accept(_)

        when:
        parser.nextPath("tree-reports.html.destination/classes/FooTest.html", false, parentHandler)
        then:
        0 * parentHandler.accept(_)

        when:
        parser.nextPath("tree-reports.html.destination/css/", true, parentHandler)
        then:
        1 * parentHandler.accept("classes")

        when:
        parser.handleParents(parentHandler)
        then:
        1 * parentHandler.accept("css")
        0 * _
    }
}
