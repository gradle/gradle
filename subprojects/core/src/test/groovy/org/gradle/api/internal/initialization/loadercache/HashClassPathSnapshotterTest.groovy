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

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class HashClassPathSnapshotterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    @Subject snapshotter = new HashClassPathSnapshotter()

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
}
