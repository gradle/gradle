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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class RelativeFileNameTransformerTest extends Specification {
    static rootDir = new File("root")

    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())

    def "returns canonical path where file outside of root"() {
        expect:
        transform(relative, file) == normaliseFileSeparators(file.canonicalPath)

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
        "another/dir/child.txt"    | "../../another/dir/child.txt"
    }

    def "returns relative path where file shared some of current dir path"() {
        when:
        def file = new File(rootDir, filePath)
        def current = new File(rootDir, "current/dir")


        then:
        transform(current, file) == relativePath

        where:
        filePath                   | relativePath
        "current/child.txt"        | "../child.txt"
        "current/dir/child.txt"    | "child.txt"
        "current/subdir"           | "../subdir"
        "current/subdir/child.txt" | "../subdir/child.txt"
    }

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

    def "finds relative path to descendant from and to both files and directories "() {
        setup:
        TestFile parentDir = testDir.createDir('parent')
        parentDir.create {
            file 'parent.txt'
            dir1 {
                file 'file1.txt'
                dir2 {
                    file 'file2.txt'
                    dir3 {
                        file 'file3.txt'
                    }
                }

            }
        }

        when:
        File startingDir = parentDir.getParentFile()

        then:
        pathToDescendant(new File("${startingDir}/$parent"), new File("${startingDir}/$child")) == expected

        where:
        parent              | child                             | expected
        "parent/"           | "parent/dir1/file1.txt"           | 'dir1/file1.txt'
        "parent/parent.txt" | "parent/dir1/file1.txt"           | 'dir1/file1.txt'
        "parent/"           | "parent/dir1/"                    | 'dir1'
        "parent/dir1/"      | "parent/dir1/dir2/file2.txt"      | 'dir2/file2.txt'
        "parent/dir1/"      | "parent/dir1/dir2/dir3/file3.txt" | 'dir2/dir3/file3.txt'
    }

    String transform(File from, File to) {
        return normaliseFileSeparators(RelativeFileNameTransformer.forDirectory(rootDir, from).transform(to))
    }

    String pathToDescendant(File parent, File to) {
        assert parent.exists()
        assert to.exists()
        return normaliseFileSeparators(RelativeFileNameTransformer.from(parent).transform(to))
    }
}
