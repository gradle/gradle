/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.file

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class FileHierarchySetTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "creates from a single file"() {
        def dir = tmpDir.createDir("dir")

        expect:
        def set = from(dir)
        set.contains(dir)
        set.contains(dir.file("child"))
        !set.contains(dir.parentFile)
        !set.contains(tmpDir.file("dir2"))
        !set.contains(tmpDir.file("d"))
    }

    def "creates from empty collection"() {
        expect:
        def set = from()
        !set.contains(tmpDir.file("any"))
    }

    def "creates from collection containing single file"() {
        def dir = tmpDir.createDir("dir")

        expect:
        def set = from(dir)
        set.contains(dir)
        set.contains(dir.file("child"))
        !set.contains(dir.parentFile)
        !set.contains(tmpDir.file("dir2"))
        !set.contains(tmpDir.file("d"))
    }

    def "creates from multiple files"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("common/dir2")
        def dir3 = parent.createDir("common/dir3")

        expect:
        def set = from(dir1, dir2, dir3)
        set.contains(dir1)
        set.contains(dir2)
        set.contains(dir3)
        set.contains(dir1.file("child"))
        set.contains(dir2.file("child"))
        set.contains(dir3.file("child"))
        !set.contains(parent)
        !set.contains(dir2.parentFile)
        !set.contains(tmpDir.file("dir"))
        !set.contains(tmpDir.file("dir12"))
        !set.contains(tmpDir.file("common/dir21"))
        set.flatten() == [parent.path, "1:dir1", "1:common", "2:dir2", "2:dir3"]
    }

    def "creates from files where one file is ancestor of the others"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = dir1.createDir("dir2")

        expect:
        def set = from(dir2, dir1)
        set.contains(dir1)
        set.contains(dir2)
        set.contains(dir1.file("child"))
        set.contains(dir2.file("child"))
        !set.contains(dir1.parentFile)
        !set.contains(tmpDir.file("dir"))
        !set.contains(tmpDir.file("dir12"))
        !set.contains(tmpDir.file("dir21"))
        set.flatten() == [dir1.path]
    }

    @Issue("https://github.com/gradle/gradle/issues/11508")
    @Requires(UnitTestPreconditions.NotWindows)
    def "creates from file system root files"() {
        expect:
        def set = from(File.listRoots())
        set.contains(tmpDir.file("any"))
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'can handle more root dirs'() {
        expect:
        from(pathList.collect { new File(it) }).contains(target) == result

        where:
        pathList                 | target            | result
        ['C:\\', 'D:\\']         | 'C:\\'            | true
        ['C:\\', 'D:\\', 'E:\\'] | 'C:\\'            | true
        ['C:\\', 'D:\\']         | 'D:\\'            | true
        ['C:\\', 'D:\\']         | 'C:\\any'         | true
        ['C:\\', 'D:\\']         | 'D:\\any'         | true
        ['C:\\', 'D:\\']         | 'E:\\any'         | false
        ['C:\\', 'D:\\', 'E:\\'] | 'E:\\any'         | true
        ['C:\\', 'D:\\', 'E:\\'] | 'F:\\any'         | false
        ['C:\\', 'C:\\any']      | 'C:\\any\\thing'  | true
        ['C:\\', 'C:\\any']      | 'E:\\any\\thing'  | false
        ['C:\\any1', 'D:\\any2'] | 'C:\\any1\\thing' | true
        ['C:\\any1', 'D:\\any2'] | 'D:\\any2\\thing' | true
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def 'can handle complicated roots'() {
        expect:
        rootsOf(from([
            "/tulry/nested-cli/nested-cli-nested/buildSrc",
            "/tulry/nested-cli/buildSrc/buildSrc",
            "/tulry/nested/buildSrc"
        ].collect({ new File(it) }))) == [
            "/tulry/nested-cli/nested-cli-nested/buildSrc",
            "/tulry/nested-cli/buildSrc/buildSrc",
            "/tulry/nested/buildSrc"
        ]
    }

    @Requires(UnitTestPreconditions.Unix)
    def 'can handle more dirs on Unix'() {
        expect:
        from(pathList.collect { new File(it) }).contains(target) == result

        where:
        pathList                       | target               | result
        ['/var', '/home']              | '/usr'               | false
        ['/var/log', '/var/home', '/'] | '/usr'               | true
        ['/', '/home']                 | '/'                  | true
        ['/home', '/']                 | '/'                  | true
        ['/home', '/']                 | '/home'              | true
        ['/', '/home']                 | '/home'              | true
        ['/', '/']                     | '/'                  | true
        ['/var', '/home']              | '/var'               | true
        ['/var', '/home']              | '/home'              | true
        ['/var', '/home', '/usr']      | '/home'              | true
        ['/var', '/home', '/usr']      | '/usr'               | true
        ['/var', '/home', '/usr']      | '/bin'               | false
        ['/var/log', '/home/my']       | '/var/log'           | true
        ['/var/log', '/home/my']       | '/home/my/documents' | true
        ['/var', '/var/log']           | '/var/log/kern.log'  | true
        ['/var', '/var/log']           | '/var/other'         | true
        ['/var', '/var/log']           | '/home'              | false
    }

    def "can add dir to empty set"() {
        def empty = from()
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")

        expect:
        def s1 = empty.plus(dir1)
        s1.contains(dir1)
        !s1.contains(dir2)

        def s2 = empty.plus(dir2)
        !s2.contains(dir1)
        s2.contains(dir2)
    }

    def "can add dir to singleton set"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def tooMany = parent.createDir("dir12")
        def tooFew = parent.createDir("dir")
        def child = dir1.createDir("child1")
        def single = from(dir1)

        expect:
        def s1 = single.plus(dir2)
        s1.contains(dir1)
        s1.contains(child)
        s1.contains(dir2)
        !s1.contains(dir3)
        !s1.contains(tooFew)
        !s1.contains(tooMany)
        !s1.contains(parent)
        s1.flatten() == [parent.path, "1:dir1", "1:dir2"]

        def s2 = single.plus(dir1)
        s2.contains(dir1)
        s2.contains(child)
        !s2.contains(dir2)
        !s2.contains(dir3)
        !s2.contains(tooFew)
        !s2.contains(tooMany)
        !s2.contains(parent)
        s2.flatten() == [dir1.path]

        def s3 = single.plus(child)
        s3.contains(dir1)
        s3.contains(child)
        !s3.contains(dir2)
        !s3.contains(dir3)
        !s3.contains(tooFew)
        !s3.contains(tooMany)
        !s3.contains(parent)
        s3.flatten() == [dir1.path]

        def s4 = single.plus(parent)
        s4.contains(dir1)
        s4.contains(child)
        s4.contains(dir2)
        s4.contains(dir3)
        s4.contains(parent)
        s4.flatten() == [parent.path]

        def s5 = single.plus(tooFew)
        s5.contains(dir1)
        s5.contains(child)
        s5.contains(tooFew)
        !s5.contains(dir2)
        !s5.contains(tooMany)
        !s5.contains(parent)
        s5.flatten() == [parent.path, "1:dir1", "1:dir"]

        def s6 = single.plus(tooMany)
        s6.contains(dir1)
        s6.contains(child)
        s6.contains(tooMany)
        !s6.contains(dir2)
        !s6.contains(tooFew)
        !s6.contains(parent)
        s6.flatten() == [parent.path, "1:dir1", "1:dir12"]
    }

    def "can add dir to multi set"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def other = parent.createDir("dir4")
        def child = dir1.createDir("child1")
        def multi = from(dir1, dir2)

        expect:
        def s1 = multi.plus(dir3)
        s1.contains(dir1)
        s1.contains(child)
        s1.contains(dir2)
        s1.contains(dir3)
        !s1.contains(other)
        !s1.contains(parent)
        s1.flatten() == [parent.path, "1:dir1", "1:dir2", "1:dir3"]

        def s2 = multi.plus(dir2)
        s2.contains(dir1)
        s2.contains(child)
        s2.contains(dir2)
        !s2.contains(dir3)
        !s2.contains(other)
        !s2.contains(parent)
        s2.flatten() == [parent.path, "1:dir1", "1:dir2"]

        def s3 = multi.plus(child)
        s3.contains(dir1)
        s3.contains(child)
        s3.contains(dir2)
        !s3.contains(dir3)
        !s3.contains(other)
        !s3.contains(parent)
        s3.flatten() == [parent.path, "1:dir1", "1:dir2"]

        def s4 = multi.plus(parent)
        s4.contains(dir1)
        s4.contains(child)
        s4.contains(dir2)
        s4.contains(other)
        s4.contains(parent)
        s4.flatten() == [parent.path]
    }

    def "splits and merges prefixes as directories are added"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1dir2 = dir1.createDir("dir2")
        def dir1dir2dir3 = dir1dir2.createDir("dir3")
        def dir1dir2dir4 = dir1dir2.createDir("dir4")
        def dir1dir5 = dir1.createDir("dir5/and/more")
        def dir6 = parent.createDir("dir6")

        expect:
        def s1 = from(dir1dir2dir3, dir1dir5)
        s1.flatten() == [dir1.path, "1:dir2/dir3", "1:dir5/and/more"]

        def s2 = s1.plus(dir1dir2dir4)
        s2.flatten() == [dir1.path, "1:dir2", "2:dir3", "2:dir4", "1:dir5/and/more"]

        def s3 = s2.plus(dir6)
        s3.flatten() == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5/and/more", "1:dir6"]

        def s4 = s3.plus(dir1dir2)
        s4.flatten() == [parent.path, "1:dir1", "2:dir2", "2:dir5/and/more", "1:dir6"]

        def s5 = s4.plus(dir1)
        s5.flatten() == [parent.path, "1:dir1", "1:dir6"]

        def s6 = s3.plus(dir1)
        s6.flatten() == [parent.path, "1:dir1", "1:dir6"]

        def s7 = s3.plus(parent)
        s7.flatten() == [parent.path]
    }

    def "has a nice toString representation"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("common/dir2")
        def dir3 = parent.createDir("common/dir3")
        def set = from(dir1, dir2, dir3)

        expect:
        set.toString() == """
            ${parent.absolutePath}
              dir1
              common
                dir2
                dir3
        """.stripIndent().trim()
    }

    def "root paths are calculated correctly"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1Child = dir1.file("child")
        def commonDir2 = parent.createDir("common/dir2")
        def commonDir3 = parent.createDir("common/dir3")

        expect:
        rootsOf(from()) == []
        rootsOf(from(dir1)) == [dir1.absolutePath]
        rootsOf(from(dir1, dir1Child)) == [dir1.absolutePath]
        rootsOf(from(commonDir2)) == [commonDir2.absolutePath]
        rootsOf(from(dir1, commonDir2, commonDir3)) == [dir1.absolutePath, commonDir2.absolutePath, commonDir3.absolutePath]
    }

    private static FileHierarchySet from(File... roots) {
        from(roots as List)
    }

    private static FileHierarchySet from(Iterable<File> roots) {
        def set = FileHierarchySet.empty()
        for (def root : roots) {
            set = set.plus(root)
        }
        return set
    }

    private static List<String> rootsOf(FileHierarchySet set) {
        def roots = []
        set.visitRoots((root -> roots.add(root)))
        return roots
    }
}
