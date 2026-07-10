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
    def exitHandler = Mock(Runnable)

    def "can work when empty"() {
        def parser = new RelativePathParser("tree-some/")

        expect:
        parser.root

        when:
        parser.exitToRoot(exitHandler)
        then:
        0 * _
    }

    def "can exit when moved to different root"() {
        def parser = new RelativePathParser("tree-some/")

        when:
        boolean outsideOfRoot = parser.nextPath("tree-other/", true, exitHandler)
        then:
        0 * _
        outsideOfRoot

        when:
        parser.exitToRoot(exitHandler)
        then:
        0 * _
    }

    def "can parse sequence of relative paths"() {
        def parser = new RelativePathParser("tree-some/")
        boolean outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/first/", true, exitHandler)
        then:
        0 * exitHandler.run()
        then:
        parser.name == "first"
        parser.relativePath == "first"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/first/file.txt", false, exitHandler)
        then:
        0 * exitHandler.run()
        then:
        parser.name == "file.txt"
        parser.relativePath == "first/file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/", true, exitHandler)
        then:
        1 * exitHandler.run()
        then:
        parser.name == "second"
        parser.relativePath == "second"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/", true, exitHandler)
        then:
        0 * exitHandler.run()
        then:
        parser.name == "third"
        parser.relativePath == "second/third"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/forth/", true, exitHandler)
        then:
        0 * exitHandler.run()
        then:
        parser.name == "forth"
        parser.relativePath == "second/third/forth"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/second/third/one-file.txt", false, exitHandler)
        then:
        1 * exitHandler.run()
        then:
        parser.name == "one-file.txt"
        parser.relativePath == "second/third/one-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/another-file.txt", false, exitHandler)
        then:
        2 * exitHandler.run()
        then:
        parser.name == "another-file.txt"
        parser.relativePath == "another-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-some/more-file.txt", false, exitHandler)
        then:
        0 * exitHandler.run()
        then:
        parser.name == "more-file.txt"
        parser.relativePath == "more-file.txt"
        !parser.root
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-other/", true, exitHandler)
        then:
        outsideOfRoot
    }

    def "can parse with dots"() {
        def parser = new RelativePathParser("tree-reports.html.destination/")
        boolean outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/classes/", true, exitHandler)
        then:
        0 * exitHandler.run()
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/classes/FooTest.html", false, exitHandler)
        then:
        0 * exitHandler.run()
        !outsideOfRoot

        when:
        outsideOfRoot = parser.nextPath("tree-reports.html.destination/css/", true, exitHandler)
        then:
        1 * exitHandler.run()
        !outsideOfRoot

        when:
        parser.exitToRoot(exitHandler)
        then:
        1 * exitHandler.run()
        0 * _
    }
}
