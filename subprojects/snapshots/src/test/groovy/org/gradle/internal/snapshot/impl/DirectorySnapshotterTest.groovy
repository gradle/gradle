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

package org.gradle.internal.snapshot.impl

import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.fingerprint.impl.PatternSetSnapshottingFilter
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class DirectorySnapshotterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def fileHasher = new TestFileHasher()
    def directorySnapshotter = new DirectorySnapshotter(fileHasher, new StringInterner(), [])

    def "should snapshot without filters"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def nestedSiblingTextFile = rootDir.file("a/c/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        def visited = []
        def relativePaths = []

        def actuallyFiltered = new AtomicBoolean(false)
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, actuallyFiltered)
        snapshot.accept(new RelativePathTrackingVisitor() {
            @Override
            void visit(String absolutePath, Deque<String> relativePath) {
                visited << absolutePath
                relativePaths << relativePath.join("/")
            }
        })

        then:
        ! actuallyFiltered.get()
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        visited.contains(notTextFile.absolutePath)
        visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == (['root'] + [
            'a',
            'a/b', 'a/b/c.txt',
            'a/c', 'a/c/c.txt', 'a/b/c.html',
            'subdir1', 'subdir1/a', 'subdir1/a/b', 'subdir1/a/b/c.html',
            'a.txt'
        ].collect { 'root/' + it }) as Set
    }

    def "should snapshot file system root"() {
        given:
        def fileSystemRoot = fileSystemRoot()
        def patterns = new PatternSet().exclude("*")
        def actuallyFiltered = new AtomicBoolean(false)

        when:
        def snapshot = directorySnapshotter.snapshot(fileSystemRoot, directoryWalkerPredicate(patterns) , actuallyFiltered)

        then:
        snapshot.absolutePath == fileSystemRoot
        snapshot.name == ""
    }

    def "should snapshot with filters"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def nestedSiblingTextFile = rootDir.file("a/c/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        def visited = []
        def relativePaths = []

        def actuallyFiltered = new AtomicBoolean(false)
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patterns), actuallyFiltered)
        snapshot.accept(new RelativePathTrackingVisitor() {
            @Override
            void visit(String absolutePath, Deque<String> relativePath) {
                visited << absolutePath
                relativePaths << relativePath.join("/")
            }
        })

        then:
        actuallyFiltered.get()
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        !visited.contains(notTextFile.absolutePath)
        !visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == [
            'root',
            'root/a',
            'root/a/b', 'root/a/b/c.txt',
            'root/a/c', 'root/a/c/c.txt',
            'root/a.txt'
        ] as Set
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "broken symlinks are snapshotted as missing"() {
        def rootDir = tmpDir.createDir("root")
        rootDir.file('brokenSymlink').createLink("linkTarget")
        assert rootDir.listFiles()*.exists() == [false]
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))
        then:
        snapshot.children.size == 1
        def brokenSymlinkSnapshot = snapshot.children[0]
        brokenSymlinkSnapshot.class == MissingFileSnapshot
        brokenSymlinkSnapshot.accessType == AccessType.VIA_SYMLINK

        when:
        rootDir.file("linkTarget").createFile() // unbreak my heart
        and:
        snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))
        then:
        snapshot.children*.class == [RegularFileSnapshot, RegularFileSnapshot]
        snapshot.children*.accessType == [AccessType.VIA_SYMLINK, AccessType.DIRECT]
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "can snapshot a directory with cycles introduced via symlinks"() {
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.file("dir").createDir()
        dir.file('subdir').createLink(dir)
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))
        then:
        snapshot.children.size == 1
        def dirSnapshot = snapshot.children[0]
        dirSnapshot.class == CompleteDirectorySnapshot
        dirSnapshot.accessType == AccessType.DIRECT
        dirSnapshot.children == []
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "can snapshot a directory with symlink cycle inside"() {
        def rootDir = tmpDir.createDir("root")
        def first = rootDir.file("first")
        def second = rootDir.file("second")
        def third = rootDir.file("third")
        first.createLink(second)
        second.createLink(third)
        third.createLink(first)
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))
        then:
        snapshot.children.size == 3
        snapshot.children.every { it.class == MissingFileSnapshot }
        snapshot.children.every { it.accessType == AccessType.VIA_SYMLINK }
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "unreadable files and directories are snapshotted as missing"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file('readableFile').createFile()
        rootDir.file('readableDirectory').createDir()
        rootDir.file('unreadableFile').createFile().makeUnreadable()
        rootDir.file('unreadableDirectory').createDir().makeUnreadable()

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))

        then:
        assert snapshot instanceof CompleteDirectorySnapshot
        snapshot.children.collectEntries { [it.name, it.class] } == [
            readableDirectory: CompleteDirectorySnapshot,
            readableFile: RegularFileSnapshot,
            unreadableDirectory: MissingFileSnapshot,
            unreadableFile: MissingFileSnapshot
        ]
        snapshot.children.every { it.accessType == AccessType.DIRECT }

        cleanup:
        rootDir.listFiles()*.makeReadable()
    }


    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    @Issue("https://github.com/gradle/gradle/issues/2552")
    def "named pipe snapshots to MissingFileSnapshot"() {
        def rootDir = tmpDir.createDir("root")
        def pipe = rootDir.file("testPipe").createNamedPipe()

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, new AtomicBoolean(false))
        then:
        assert snapshot instanceof CompleteDirectorySnapshot
        snapshot.children.collectEntries { [it.name, it.class] } == [
            testPipe: MissingFileSnapshot
        ]
        snapshot.children.every { it.accessType == AccessType.DIRECT }

        cleanup:
        pipe.delete()
    }

    def "default excludes are correctly parsed"() {
        def defaultExcludes = new DirectorySnapshotter.DefaultExcludes(DirectoryScanner.getDefaultExcludes() as List)

        expect:
        DirectoryScanner.getDefaultExcludes() as Set == ['**/%*%', '**/.git/**', '**/SCCS', '**/.bzr', '**/.hg/**', '**/.bzrignore', '**/.git', '**/SCCS/**', '**/.hg', '**/.#*', '**/vssver.scc', '**/.bzr/**', '**/._*', '**/#*#', '**/*~', '**/CVS', '**/.hgtags', '**/.svn/**', '**/.hgignore', '**/.svn', '**/.gitignore', '**/.gitmodules', '**/.hgsubstate', '**/.gitattributes', '**/CVS/**', '**/.hgsub', '**/.DS_Store', '**/.cvsignore'] as Set

        ['%some%', 'SCCS', '.bzr', '.bzrignore', '.git', '.hg', '.#anything', '.#', 'vssver.scc', '._something', '#anyt#', '##', 'temporary~', '~'].each {
            assert defaultExcludes.excludeFile(it)
        }

        ['.git', '.hg', 'CVS'].each {
            assert defaultExcludes.excludeDir(it)
        }

        !defaultExcludes.excludeDir('#some#')
        !defaultExcludes.excludeDir('.cvsignore')

        !defaultExcludes.excludeFile('.svnsomething')
        !defaultExcludes.excludeFile('#some')
    }

    private static String fileSystemRoot() {
        "${Paths.get("").toAbsolutePath().root}"
    }

    private static SnapshottingFilter.DirectoryWalkerPredicate directoryWalkerPredicate(PatternSet patternSet) {
        return new PatternSetSnapshottingFilter(patternSet, TestFiles.fileSystem()).asDirectoryWalkerPredicate
    }
}

abstract class RelativePathTrackingVisitor implements FileSystemSnapshotVisitor {
    private Deque<String> relativePath = new ArrayDeque<String>()

    @Override
    boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        relativePath.addLast(directorySnapshot.name)
        visit(directorySnapshot.absolutePath, relativePath)
        return true
    }

    @Override
    void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
        relativePath.addLast(fileSnapshot.name)
        visit(fileSnapshot.absolutePath, relativePath)
        relativePath.removeLast()
    }

    @Override
    void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        relativePath.removeLast()
    }

    abstract void visit(String absolutePath, Deque<String> relativePath)
}
