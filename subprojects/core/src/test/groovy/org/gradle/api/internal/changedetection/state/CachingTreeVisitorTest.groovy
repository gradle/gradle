/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class CachingTreeVisitorTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    @Subject
    CachingTreeVisitor treeVisitor = new CachingTreeVisitor()

    def "should return list of file details and cache it once"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees()

        when:
        def fileDetails = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        fileDetails.size() == 8
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 5
        treeVisitor.cachedTrees.size() == 1

        when:
        def fileDetails2 = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        treeVisitor.cachedTrees.size() == 1
        fileDetails2.is(fileDetails)
    }

    def "should cache list of file details when there is a pattern or filter"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees(includePattern, includeFilter)

        when:
        def fileDetails = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        fileDetails.size() == 6
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 3
        treeVisitor.cachedTrees.size() == 1

        when:
        fileDetails = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        fileDetails.size() == 6
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 3
        treeVisitor.cachedTrees.size() == 1

        where:
        includePattern | includeFilter
        "**/*.txt"     | null
        null           | "**/*.txt"
        "**/*.txt"     | "**/*.txt"
    }

    def "should reuse and filter previous cached list of file details when there is a pattern or filter"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees()

        when:
        def fileDetails = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        fileDetails.size() == 8
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 5
        treeVisitor.cachedTrees.size() == 1

        when:
        def fileTreesFiltered = resolveAsFileTrees(includePattern, includeFilter)
        fileDetails = treeVisitor.visitTreeForSnapshotting(fileTreesFiltered[0], true).entries

        then:
        fileDetails.size() == 6
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 3
        treeVisitor.cachedTrees.size() == 1
        with(treeVisitor.cachedTrees.getIfPresent(testDir.getTestDirectory().absolutePath)) {
            assert noPatternTree != null
            assert treesPerPattern.size() == 1
        }

        when:
        fileDetails = treeVisitor.visitTreeForSnapshotting(fileTreesFiltered[0], true).entries

        then:
        fileDetails.size() == 6
        fileDetails.count { it.isDirectory() } == 3
        fileDetails.count { !it.isDirectory() } == 3
        treeVisitor.cachedTrees.size() == 1
        with(treeVisitor.cachedTrees.getIfPresent(testDir.getTestDirectory().absolutePath)) {
            assert noPatternTree != null
            assert treesPerPattern.size() == 1
        }

        where:
        includePattern | includeFilter
        "**/*.txt"     | null
        null           | "**/*.txt"
        "**/*.txt"     | "**/*.txt"
    }


    def "should not use cached when allowReuse == false but should still add it to cache"() {
        given:
        createSampleFiles()
        def fileTrees = resolveAsFileTrees()

        when:
        def fileDetails = treeVisitor.visitTreeForSnapshotting(fileTrees[0], false).entries

        then:
        fileDetails.size() == 8
        treeVisitor.cachedTrees.size() == 1

        when:
        def fileDetails2 = treeVisitor.visitTreeForSnapshotting(fileTrees[0], false).entries

        then:
        !fileDetails2.is(fileDetails)
        fileDetails2.collect { it.file } as Set == fileDetails.collect { it.file } as Set
        treeVisitor.cachedTrees.size() == 1

        when:
        def fileDetails3 = treeVisitor.visitTreeForSnapshotting(fileTrees[0], true).entries

        then:
        fileDetails3.is(fileDetails2)
        treeVisitor.cachedTrees.size() == 1
    }

    private def createSampleFiles() {
        [testDir.createFile("a/file1.txt"),
         testDir.createFile("a/b/file2.txt"),
         testDir.createFile("a/b/c/file3.txt"),
         testDir.createFile("a/b/c/file4.md"),
         testDir.createFile("a/file5.md"),]
    }

    private List<FileTreeInternal> resolveAsFileTrees(includePattern = null, includeFilter = null) {
        def fileResolver = TestFiles.resolver()

        def directorySet = new DefaultSourceDirectorySet("files", fileResolver, new DefaultDirectoryFileTreeFactory())
        directorySet.srcDir(testDir.getTestDirectory())
        if (includePattern) {
            directorySet.include(includePattern)
        }
        if (includeFilter) {
            directorySet.filter.include(includeFilter)
        }
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(fileResolver);
        context.add(directorySet);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();
        fileTrees
    }
}
