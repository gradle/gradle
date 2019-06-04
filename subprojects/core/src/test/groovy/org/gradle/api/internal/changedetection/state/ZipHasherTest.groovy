/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.changedetection.state


import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule

class ZipHasherTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    ZipHasher zipHasher = new ZipHasher(new RuntimeClasspathResourceHasher(), ResourceFilter.FILTER_NOTHING)

    def "adding an empty jar inside another jar changes the hashcode"() {
        given:
        def outerContent = tmpDir.createDir("outer")
        def outer = tmpDir.file("outer.jar")
        outerContent.zipTo(outer)
        def originalHash = zipHasher.hash(snapshot(outer))

        when:
        def innerContent = tmpDir.createDir("inner")
        def inner = outerContent.file("inner.jar")
        innerContent.zipTo(inner)
        outerContent.zipTo(outer)
        def newHash = zipHasher.hash(snapshot(outer))

        then:
        originalHash != newHash
    }

    def "relative path of nested zip entries is tracked"() {
        given:
        def outerContent1 = tmpDir.createDir("outer1")
        def innerContent1 = tmpDir.createDir("inner1")
        innerContent1.file("foo") << "Foo"
        def inner1 = outerContent1.file("inner1.jar")
        innerContent1.zipTo(inner1)
        def outer1 = tmpDir.file("outer1.jar")
        outerContent1.zipTo(outer1)
        def hash1 = zipHasher.hash(snapshot(outer1))

        def outerContent2 = tmpDir.createDir("outer2")
        def innerContent2 = tmpDir.createDir("inner2")
        innerContent2.file("foo") << "Foo"
        def inner2 = outerContent2.file("inner2.jar")
        innerContent2.zipTo(inner2)
        def outer2 = tmpDir.file("outer2.jar")
        outerContent2.zipTo(outer2)
        def hash2 = zipHasher.hash(snapshot(outer2))

        expect:
        hash1 != hash2
    }

    private static RegularFileSnapshot snapshot(TestFile file) {
        new RegularFileSnapshot(file.path, file.name, HashCode.fromInt(0), 0)
    }
}
