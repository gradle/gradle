/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultClasspathSnapshotter
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultClasspathHasherTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def stringInterner = Mock(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def snapshotter = new DefaultClasspathHasher(new DefaultClasspathSnapshotter(new DefaultFileHasher(), stringInterner, TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory()))

    def "classpaths are different if file contents are different"() {
        def file1 = temp.file("a.txt") << "a"
        def file2 = temp.file("b.txt") << "b"

        def a = snapshotter.hash(new DefaultClassPath(file1))
        def b = snapshotter.hash(new DefaultClassPath(file2))

        expect:
        a != b
    }

    def "classpaths content hash are equal when file names don't match"() {
        def fa = temp.file("a.txt") << "a"
        def fb = temp.file("b.txt") << "a" //same content

        def a = snapshotter.hash(new DefaultClassPath(fa))
        def b = snapshotter.hash(new DefaultClassPath(fb))

        expect:
        a == b
    }

    def "classpaths are different when files have different order"() {
        def fa = temp.file("a.txt") << "a"
        def fb = temp.file("b.txt") << "ab"

        def a = snapshotter.hash(new DefaultClassPath(fa, fb))
        def b = snapshotter.hash(new DefaultClassPath(fb, fa))

        expect:
        a != b
    }

    def "classpaths are equal if all files are the same"() {
        def files = [temp.file("a.txt") << "a", temp.file("b.txt") << "b"]
        def a = snapshotter.hash(new DefaultClassPath(files))
        def b = snapshotter.hash(new DefaultClassPath(files))

        expect:
        a == b
    }

    def "classpaths are equal if dirs are the same"() {
        temp.file("dir/a.txt") << "a"
        temp.file("dir/b.txt") << "b"
        temp.file("dir/dir2/c.txt") << "c"
        def a = snapshotter.hash(new DefaultClassPath(temp.createDir("dir")))
        def b = snapshotter.hash(new DefaultClassPath(temp.createDir("dir")))

        expect:
        a == b
    }

    def "classpaths are the same if different if dir names are different"() {
        temp.file("dir1/a.txt") << "a"
        temp.file("dir2/a.txt") << "a"
        def a = snapshotter.hash(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.hash(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a == b
    }

    def "classpaths are different if sub-dir names are different"() {
        temp.file("dir1/sub-dir1/a.txt") << "a"
        temp.file("dir2/sub-dir2/a.txt") << "a"
        def a = snapshotter.hash(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.hash(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a != b
    }

    def "classpaths content hash are equal if dir names are different"() {
        temp.file("dir1/a.txt") << "a"
        temp.file("dir2/a.txt") << "a"
        def a = snapshotter.hash(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.hash(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a == b
    }

    def "classpaths are the same for 2 empty dirs"() {
        def a = snapshotter.hash(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.hash(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a == b
    }

    def "empty snapshots are the same"() {
        when:
        def s1 = snapshotter.hash(new DefaultClassPath(new File(temp.createDir("dir1"), "missing")));
        def s2 = snapshotter.hash(new DefaultClassPath(new File(temp.createDir("dir2"), "missing")));

        then:
        s1 == s2
    }
}
