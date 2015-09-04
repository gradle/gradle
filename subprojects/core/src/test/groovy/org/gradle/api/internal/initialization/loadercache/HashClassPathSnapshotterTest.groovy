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

import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.cache.internal.NonThreadsafeInMemoryStore
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class HashClassPathSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject snapshotter = new HashClassPathSnapshotter(new CachingFileSnapshotter(new DefaultHasher(), new NonThreadsafeInMemoryStore()))

    def "classpaths are different if file hashes are different"() {
        def file = temp.file("a.txt")

        file << "a"; def a = snapshotter.snapshot(new DefaultClassPath(file))
        file << "b"; def b = snapshotter.snapshot(new DefaultClassPath(file))

        expect:
        a != b
        a.hashCode() != b.hashCode()
    }

    def "classpaths are different when file names don't match"() {
        def fa = temp.file("a.txt") << "a"
        def fb = temp.file("b.txt") << "a" //same content

        def a = snapshotter.snapshot(new DefaultClassPath(fa))
        def b = snapshotter.snapshot(new DefaultClassPath(fb))

        expect:
        a != b
        a.hashCode() != b.hashCode()
    }

    def "classpaths are different when files have different order"() {
        def fa = temp.file("a.txt") << "a"
        def fb = temp.file("b.txt") << "a"

        def a = snapshotter.snapshot(new DefaultClassPath(fa, fb))
        def b = snapshotter.snapshot(new DefaultClassPath(fb, fa))

        expect:
        a != b
        a.hashCode() != b.hashCode()
    }

    def "classpaths are equal if all files are the same"() {
        def files = [temp.file("a.txt") << "a", temp.file("b.txt") << "b"]
        def a = snapshotter.snapshot(new DefaultClassPath(files))
        def b = snapshotter.snapshot(new DefaultClassPath(files))

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def "classpaths are equal if dirs are the same"() {
        temp.file("dir/a.txt") << "a"; temp.file("dir/b.txt") << "b"; temp.file("dir/dir2/c.txt") << "c"
        def a = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir")))
        def b = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir")))

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def "classpaths are different if dir names are different"() {
        temp.file("dir1/a.txt") << "a"; temp.file("dir2/a.txt") << "a"
        def a = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a != b
        a.hashCode() != b.hashCode()
    }

    def "classpaths are the same for 2 empty dirs"() {
        def a = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir1")))
        def b = snapshotter.snapshot(new DefaultClassPath(temp.createDir("dir2")))

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def "empty snapshots are the same"() {
        when:
        def s1 = snapshotter.snapshot(new DefaultClassPath(new File(temp.createDir("dir1"), "missing")));
        def s2 = snapshotter.snapshot(new DefaultClassPath(new File(temp.createDir("dir2"), "missing")));

        then:
        s1 == s2
    }
}