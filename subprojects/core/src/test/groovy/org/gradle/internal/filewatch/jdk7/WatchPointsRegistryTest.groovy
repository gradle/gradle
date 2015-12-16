/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7

import org.gradle.api.internal.file.FileSystemSubset
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification


@UsesNativeServices
class WatchPointsRegistryTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();

    WatchPointsRegistry registry
    TestFile rootDir

    def setup() {
        registry = new WatchPointsRegistry()
        rootDir = testDir.createDir("root")
    }

    def "correct roots are calculated when registry is empty"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b/d")]
        FileSystemSubset fileSystemSubset = createFileSystemSubset(dirs)

        when:
        def delta = registry.appendFileSystemSubset(fileSystemSubset)

        then:
        delta.startingWatchPoints as Set == dirs as Set
    }

    def "correct roots are calculated when adding entries to registry"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b/d")]

        when:
        def delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[0]))

        then:
        delta.startingWatchPoints as Set == [dirs[0]] as Set

        when:
        delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[1]))

        then:
        delta.startingWatchPoints as Set == [dirs[1]] as Set
    }


    def "child doesn't get added when parent has already been added"() {
        given:
        def dirs = [rootDir.createDir("a/b"), rootDir.createDir("a/b/c")]

        when:
        def delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[0]))

        then:
        delta.startingWatchPoints as Set == [dirs[0]] as Set

        when:
        delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[1]))

        then:
        delta.startingWatchPoints.size() == 0
    }

    def "only parent gets added when child is added at the same time as the parent - parent before child"() {
        given:
        def dirs = [rootDir.createDir("a/b"), rootDir.createDir("a/b/c")]

        when:
        def delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs))

        then:
        delta.startingWatchPoints as Set == [dirs[0]] as Set
    }

    def "parent gets added when child has been added before it"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b")]

        when:
        def delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[0]))

        then:
        delta.startingWatchPoints as Set == [dirs[0]] as Set

        when:
        delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs[1]))

        then:
        delta.startingWatchPoints as Set == [dirs[1]] as Set
    }

    def "only parent gets added when child is added at the same time as the parent - child before parent"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b")]

        when:
        def delta = registry.appendFileSystemSubset(createFileSystemSubset(dirs))

        then:
        delta.startingWatchPoints as Set == [dirs[1]]as Set
    }

    private static FileSystemSubset createFileSystemSubset(Iterable<File> dirs) {
        def builder = FileSystemSubset.builder()
        dirs.each { builder.add it }
        builder.build()
    }

    private static FileSystemSubset createFileSystemSubset(File dir) {
        def builder = FileSystemSubset.builder()
        builder.add dir
        builder.build()
    }
}
