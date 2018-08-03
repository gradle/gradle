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

package org.gradle.caching.internal.tasks

import spock.lang.Specification

class RelativePathParserTest extends Specification {

    def "can parse sequence of relative paths"() {
        def parser = new RelativePathParser()

        expect:
        parser.rootPath("property-some/")

        parser.nextPath("property-some/first/", true) == 0
        parser.name == "first"
        parser.relativePath == "first"

        parser.nextPath("property-some/first/file.txt", false) == 0
        parser.name == "file.txt"
        parser.relativePath == "first/file.txt"

        parser.nextPath("property-some/second/", true) == 1
        parser.name == "second"
        parser.relativePath == "second"

        parser.nextPath("property-some/second/third/", true) == 0
        parser.name == "third"
        parser.relativePath == "second/third"

        parser.nextPath("property-some/second/third/forth/", true) == 0
        parser.name == "forth"
        parser.relativePath == "second/third/forth"

        parser.nextPath("property-some/second/third/one-file.txt", false) == 1
        parser.name == "one-file.txt"
        parser.relativePath == "second/third/one-file.txt"

        parser.nextPath("property-some/second/another-file.txt",false) == 1
        parser.name == "another-file.txt"
        parser.relativePath == "second/another-file.txt"

        parser.nextPath("property-some/more-file.txt", false) == 1
        parser.name == "more-file.txt"
        parser.relativePath == "more-file.txt"
        parser.depth == 1

        parser.nextPath("property-other/", true) == 1
        parser.depth == 0
    }

    def "can parse with dots"() {
        def parser = new RelativePathParser()

        expect:
        parser.rootPath("property-reports.html.destination/")
        parser.nextPath("property-reports.html.destination/classes/", true) == 0
        parser.nextPath("property-reports.html.destination/classes/FooTest.html", false) == 0
        parser.nextPath("property-reports.html.destination/css/", true) == 1
        parser.depth == 2
    }
}
