/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.tasks.internal

import spock.lang.Specification

import static org.gradle.util.TextUtil.normaliseFileSeparators

class RelativeFileNameTransformerTest extends Specification {
    static rootDir = new File("root")

    def "returns absolute path where file outside of root"() {
        expect:
        transform(relative, file) == normaliseFileSeparators(file.absolutePath)

        where:
        relative                                          | file
        new File(rootDir, "current")                      | new File("file/outside")
        new File(rootDir, "current")                      | new File(rootDir, "subdir/../../outside/of/root")
        new File("current/outside")                       | new File(rootDir, "file/inside")
        new File(rootDir, "subdir/../../current/outside") | new File(rootDir, "file/inside")
    }

    def "returns relative path where file inside of root"() {
        when:
        def file = new File(rootDir, filePath)
        def current = new File(rootDir, "current/dir")

        then:
        transform(current, file) == relativePath

        where:
        filePath                   | relativePath
        "child.txt"                | "../../child.txt"
        "subdir"                   | "../../subdir"
        "subdir/child.txt"         | "../../subdir/child.txt"
        "subdir/another"           | "../../subdir/another"
        "subdir/another/child.txt" | "../../subdir/another/child.txt"
    }

    def "returns relative path where file shared some of current dir path"() {
        when:
        def file = new File(rootDir, filePath)
        def current = new File(rootDir, "current/dir")


        then:
        transform(current, file) == relativePath

        where:
        filePath                   | relativePath
        "current/child.txt"        | "../../current/child.txt"
        "current/dir/child.txt"    | "../../current/dir/child.txt"
        "current/subdir"           | "../../current/subdir"
        "current/subdir/child.txt" | "../../current/subdir/child.txt"
    }

    // TODO:DAZ Might be better to reduce these paths
    def "handles mixed paths inside of root"() {
        when:
        def file = new File(rootDir, filePath)
        def current = new File(rootDir, "current/dir")

        then:
        transform(current, file) == relativePath

        where:
        filePath                           | relativePath
        "subdir/down/../another"           | "../../subdir/another"
        "subdir/down/../another/child.txt" | "../../subdir/another/child.txt"
    }

    def "handles current is root"() {
        when:
        def file = new File(rootDir, filePath)
        def current = new File(rootDir.absolutePath)

        then:
        transform(current, file) == relativePath

        where:
        filePath                   | relativePath
        "child.txt"                | "child.txt"
        "subdir"                   | "subdir"
        "subdir/child.txt"         | "subdir/child.txt"
        "subdir/another"           | "subdir/another"
        "subdir/another/child.txt" | "subdir/another/child.txt"
    }

    def "handles file is root"() {
        when:
        def file = new File(rootDir.path)
        def current = new File(rootDir, currentPath)

        then:
        transform(current, file) == relativePath

        where:
        currentPath      | relativePath
        "."              | "."
        "subdir"         | ".."
        "subdir/another" | "../.."
    }

    String transform(File from, File to) {
        return normaliseFileSeparators(RelativeFileNameTransformer.forDirectory(rootDir, from).transform(to))
    }
}
