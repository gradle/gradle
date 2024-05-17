/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.watch.registry.impl.SnapshotWatchedDirectoryFinder.getDirectoryToWatch

class SnapshotWatchedDirectoryFinderTest extends Specification implements TestSnapshotFixture {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "resolves directory to watch from snapshot"() {
        def parent = temporaryFolder.createDir("parent")
        def dir = parent.createDir("dir")
        def file = parent.createFile("file.txt")
        def missingFile = parent.file("missing/file.txt")

        expect:
        getDirectoryToWatch(regularFile(file.absolutePath)) == parent
        getDirectoryToWatch(directory(dir.absolutePath, [])) == dir
        getDirectoryToWatch(missing(missingFile.absolutePath)) == parent
    }
}
