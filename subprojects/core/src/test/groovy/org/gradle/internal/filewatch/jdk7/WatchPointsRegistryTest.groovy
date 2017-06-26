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
import org.gradle.internal.filewatch.jdk7.WatchPointsRegistry.Delta
import org.gradle.internal.nativeintegration.filesystem.FileSystem
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
        registry = new WatchPointsRegistry(true, Stub(FileSystem))
        rootDir = testDir.createDir("root")
    }

    def "correct roots are calculated when registry is empty"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b/d")]
        FileSystemSubset fileSystemSubset = createFileSystemSubset(dirs)

        when:
        def delta = registry.appendFileSystemSubset(fileSystemSubset, [])

        then:
        checkWatchPoints delta, dirs
    }

    def "correct roots are calculated when adding entries to registry"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b/d")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0]]

        when:
        delta = appendInput(dirs[1])

        then:
        checkWatchPoints delta, [dirs[1]]
    }


    def "child doesn't get added when parent has already been added when createNewStartingPointsUnderExistingRoots==false"() {
        given:
        registry = new WatchPointsRegistry(false, Stub(FileSystem))
        def dirs = [rootDir.createDir("a/b"), rootDir.createDir("a/b/c")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0]]

        when:
        delta = appendInput(dirs[1], [dirs[0]])

        then:
        delta.startingWatchPoints.size() == 0
    }

    def "only parent gets added when child is added at the same time as the parent - parent before child"() {
        given:
        def dirs = [rootDir.createDir("a/b"), rootDir.createDir("a/b/c")]

        when:
        def delta = appendInput(dirs)

        then:
        checkWatchPoints delta, [dirs[0]]
    }

    def "parent gets added when child has been added before it"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0]]

        when:
        delta = appendInput(dirs[1])

        then:
        checkWatchPoints delta, [dirs[1]]
    }

    def "only parent gets added when child is added at the same time as the parent - child before parent"() {
        given:
        def dirs = [rootDir.createDir("a/b/c"), rootDir.createDir("a/b")]

        when:
        def delta = appendInput(dirs)

        then:
        delta.startingWatchPoints as Set == [dirs[1]]as Set
    }


    def "parent gets added when directory doesn't exist"() {
        given:
        def dirs = [rootDir.createDir("a/b").file("c")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0].getParentFile()]
    }

    def "directory gets added when first one doesn't exist"() {
        given:
        def dirs = [rootDir.createDir("a/b").file("c"), rootDir.createDir("a/b/d")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0].getParentFile()]

        when:
        delta = appendInput(dirs[1])

        then:
        checkWatchPoints delta, [dirs[1]]
    }

    def "directories get added when both don't originally exist"() {
        given:
        def dirs = [rootDir.file("a/b/c"), rootDir.file("a/b/d")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [dirs[0].getParentFile().getParentFile().getParentFile()]
        and: 'should fire for actual file'
        registry.shouldFire(dirs[0].createFile("file.txt"))
        and: 'should not file for files in sibling directories that arent watched'
        !registry.shouldFire(dirs[1].createFile("file2.txt"))
        and: 'should not fire for files in parent directories'
        !registry.shouldFire(dirs[0].getParentFile().getParentFile().getParentFile().createFile("file.txt"))
        !registry.shouldFire(dirs[0].getParentFile().getParentFile().createFile("file.txt"))
        !registry.shouldFire(dirs[0].getParentFile().createFile("file.txt"))

        when:
        dirs*.createDir()
        delta = appendInput(dirs[1])

        then:
        checkWatchPoints delta, [dirs[1]]
        and:
        registry.shouldFire(dirs[0].createFile("file.txt"))
        registry.shouldFire(dirs[1].createFile("file2.txt"))
    }

    def "parents for non-existing watch directories get watched"() {
        given:
        rootDir.createDir("a")
        def dirs = [rootDir.file("a/b/c/d/e"), rootDir.file("a/b/c/d2/e2")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [rootDir.file("a")]

        when:
        delta = appendInput(dirs[1], [rootDir.file("a")])

        then:
        checkWatchPoints delta, [rootDir.file("a")]

        and: 'should add watch to all parent directories of non-existing root'
        dirs.each { dir ->
            parentsUpTo(dir, rootDir).every {
                assert delta.shouldWatch(it)
                assert !delta.shouldWatch(new File(it.getParentFile(), "sibling_directory"))
                assert !registry.shouldFire(new File(it.getParentFile(), "sibling_file"))
            }
        }
    }

    def "non-existing directories get watched when events arrive later"() {
        given:
        rootDir.createDir("src")
        def dirs = [rootDir.file("src/main/java"), rootDir.file("src/main/groovy")]

        when:
        def delta = appendInput(dirs[0])

        then:
        checkWatchPoints delta, [rootDir.file("src")]

        when:
        dirs[1].mkdirs()
        delta = appendInput(dirs[1])

        then:
        checkWatchPoints delta, [dirs[1]]
        registry.shouldWatch(rootDir.file("src/main"))
    }

    def "sub directory gets watched when first input is a single file, where useDirectoryTree: #useDirectoryTree"() {
        given:
        rootDir.createDir("src")
        def towatch = [rootDir.file("src/topLevel.txt"), rootDir.file("src")]

        when:
        def delta = appendInput(towatch[0])

        then:
        checkWatchPoints delta, [rootDir.file("src")]
        !registry.shouldFire(rootDir.file("src/other.txt"))
        def subdir = rootDir.file("src/subdirectory")
        !registry.shouldWatch(subdir)
        subdir.mkdir()
        !registry.shouldWatch(subdir)

        when:
        delta = appendInput(towatch[1], [towatch[1]])

        then:
        checkWatchPoints delta, [towatch[1]]
        registry.shouldWatch(subdir)

        when:
        delta = appendInput(subdir)
        checkWatchPoints delta, [subdir]

        then:
        registry.shouldFire(rootDir.file("src/subdirectory/nested.txt"))
    }


    def parentsUpTo(File subDir, File parentDir) {
        def parents = []
        File current = subDir.parentFile
        while (current != null && current != parentDir) {
            parents.add current
            current = current.parentFile
        }
        parents
    }

    private void checkWatchPoints(Delta delta, Collection<File> files) {
        assert delta.startingWatchPoints as Set == files as Set
    }

    private Delta appendInput(Iterable<File> files, Iterable<File> currentWatchPoints = []) {
        registry.appendFileSystemSubset(createFileSystemSubset(files), currentWatchPoints)
    }

    private Delta appendInput(File file, Iterable<File> currentWatchPoints = []) {
        registry.appendFileSystemSubset(createFileSystemSubset(file), currentWatchPoints)
    }

    private static FileSystemSubset createFileSystemSubset(Iterable<File> files) {
        def builder = FileSystemSubset.builder()
        files.each { builder.add it }
        builder.build()
    }

    private static FileSystemSubset createFileSystemSubset(File file) {
        def builder = FileSystemSubset.builder()
        builder.add file
        builder.build()
    }
}
