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

class RelativePathParserTest extends Specification {
    def parentHandler = Mock(RelativePathParser.DirectoryExitHandler)

    def "can work when empty"() {
        def parser = new RelativePathParser("tree-some/")

        expect:
        parser.root

        when:
        parser.exitToRoot(parentHandler)
        then:
        0 * _
    }

    def "can exit when moved to different root"() {
        def parser = new RelativePathParser("tree-some/")

        when:
        boolean outsideOfRoot = parser.nextPath("tree-other/", true, parentHandler)
        then:
        0 * _
        outsideOfRoot

        when:
        parser.exitToRoot(parentHandler)
        then:
        0 * _
    }

    def "can parse sequence of relative paths"() {
        def parser = new RelativePathParser("tree-some/")
        boolean outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/first/", true, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        then:
        parser.name == "first"
        parser.relativePath == "first"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/first/file.txt", false, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        then:
        parser.name == "file.txt"
        parser.relativePath == "first/file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/", true, parentHandler)
        then:
        1 * parentHandler.handleExit("tree-some/first", "first")
        then:
        parser.name == "second"
        parser.relativePath == "second"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/", true, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        then:
        parser.name == "third"
        parser.relativePath == "second/third"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/forth/", true, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        then:
        parser.name == "forth"
        parser.relativePath == "second/third/forth"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/one-file.txt", false, parentHandler)
        then:
        1 * parentHandler.handleExit("tree-some/second/third/forth", "forth")
        then:
        parser.name == "one-file.txt"
        parser.relativePath == "second/third/one-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/another-file.txt", false, parentHandler)
        then:
        1 * parentHandler.handleExit("tree-some/second/third", "third")
        then:
        1 * parentHandler.handleExit("tree-some/second", "second")
        then:
        parser.name == "another-file.txt"
        parser.relativePath == "another-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/more-file.txt", false, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        then:
        parser.name == "more-file.txt"
        parser.relativePath == "more-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-other/", true, parentHandler)
        then:
        outsideOfRoot
    }

    def "can parse with dots"() {
        def parser = new RelativePathParser("tree-reports.html.destination/")
        boolean outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/classes/", true, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/classes/FooTest.html", false, parentHandler)
        then:
        0 * parentHandler.handleExit(_, _)
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/css/", true, parentHandler)
        then:
        1 * parentHandler.handleExit("tree-reports.html.destination/classes", "classes")
        !outsideOfRoot

        when:
        parser.exitToRoot(parentHandler)
        then:
        1 * parentHandler.handleExit("tree-reports.html.destination/css", "css")
        0 * _
    }
}
