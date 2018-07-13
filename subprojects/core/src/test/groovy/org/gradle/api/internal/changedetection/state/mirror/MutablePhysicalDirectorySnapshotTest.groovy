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

package org.gradle.api.internal.changedetection.state.mirror

import org.gradle.api.internal.cache.StringInterner
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class MutablePhysicalDirectorySnapshotTest extends Specification {

    def stringInterner = Stub(StringInterner) {
            intern(_) >> { String string -> string }
    }

    String basePath = '/some/location'

    def "can rebuild tree from relative paths"() {
        def root = new MutablePhysicalDirectorySnapshot(basePath, "location", stringInterner)
        def expectedRelativePaths = ['one', 'one/two', 'one/two/some.txt', 'three', 'three/four.txt']

        when:
        root.add(["one", "two", "some.txt"] as String[], 0, fileSnapshot('one/two', 'some.txt'))
        def subdir = root.add(["three"] as String[], 0, new MutablePhysicalDirectorySnapshot("${basePath}/three", "three", stringInterner))
        subdir.add(["three", "four.txt"] as String[], 1, fileSnapshot("three", "four.txt"))
        Map<String, HashCode> files = [:]
        Set<String> relativePaths = [] as Set
        root.accept(new PhysicalSnapshotVisitor() {
            private final relativePathTracker = new RelativePathSegmentsTracker()

            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                def isRoot = relativePathTracker.root
                relativePathTracker.enter(directorySnapshot)
                if (!isRoot) {
                    files[directorySnapshot.absolutePath] = PhysicalDirectorySnapshot.SIGNATURE
                    relativePaths.add(relativePathTracker.relativePath.join("/"))
                }
                return true
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                files[fileSnapshot.absolutePath] = fileSnapshot.contentHash
                relativePathTracker.enter(fileSnapshot)
                relativePaths.add(relativePathTracker.relativePath.join("/"))
                relativePathTracker.leave()
            }

            @Override
            void postVisitDirectory() {
                relativePathTracker.leave()
            }
        })

        then:
        normalizeFileSeparators(files.keySet()) == expectedRelativePaths.collect { "/some/location/$it".toString() } as Set
        relativePaths == expectedRelativePaths as Set
    }

    private static Set<String> normalizeFileSeparators(Set<String> paths) {
        paths.collect { TextUtil.normaliseFileSeparators(it) } as Set
    }

    def "cannot replace a file with a directory"() {
        def root = new MutablePhysicalDirectorySnapshot(basePath, "location", stringInterner)
        def relativePath = ["some", "file.txt"] as String[]
        root.add(relativePath, 0, fileSnapshot("some", "file.txt"))

        when:
        root.add(relativePath, 0, new MutablePhysicalDirectorySnapshot("${basePath}/some/file.txt", "file.txt", stringInterner))

        then:
        thrown IllegalStateException

    }

    def "cannot replace a directory with a file"() {
        def root = new MutablePhysicalDirectorySnapshot(basePath, "location", stringInterner)
        def relativePath = ["some", "file.txt"] as String[]
        root.add(relativePath, 0, new MutablePhysicalDirectorySnapshot("${basePath}/some/dir", "dir", stringInterner))

        when:
        root.add(relativePath, 0, fileSnapshot("some", "file.txt"))

        then:
        thrown IllegalStateException

    }

    private PhysicalFileSnapshot fileSnapshot(String relativePath, String name) {
        new PhysicalFileSnapshot("${basePath}/${relativePath.empty ? "" : (relativePath + '/')}${name}", name, HashCode.fromInt(1234), 1234)
    }
}
