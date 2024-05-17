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
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.fingerprint.impl.PatternSetSnapshottingFilter
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotUtil
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Paths
import java.util.function.Consumer

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class DirectorySnapshotterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def fileHasher = new TestFileHasher()
    def statisticsCollector = Stub(DirectorySnapshotterStatistics.Collector)
    def directorySnapshotter = new DirectorySnapshotter(fileHasher, new StringInterner(), [], statisticsCollector)
    List<FileSystemLocationSnapshot> unfilteredSubsnapshots = []
    Consumer<FileSystemLocationSnapshot> unfilteredSubSnapshotsCollector = { FileSystemLocationSnapshot snapshot ->
        unfilteredSubsnapshots.add(snapshot)
    }

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

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        unfilteredSubsnapshots == [snapshot]
        0 * _

        when:
        def visited = SnapshotVisitorUtil.getAbsolutePaths(snapshot, true)
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot, true)

        then:
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        visited.contains(notTextFile.absolutePath)
        visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == [
            '',
            'a',
            'a/b', 'a/b/c.txt',
            'a/c', 'a/c/c.txt', 'a/b/c.html',
            'subdir1', 'subdir1/a', 'subdir1/a/b', 'subdir1/a/b/c.html',
            'a.txt'
        ] as Set
    }

    def "should snapshot file system root"() {
        given:
        def fileSystemRoot = fileSystemRoot()
        def patterns = new PatternSet().exclude("**/*")

        when:
        def snapshot = directorySnapshotter.snapshot(fileSystemRoot, directoryWalkerPredicate(patterns), [:], unfilteredSubSnapshotsCollector)

        then:
        unfilteredSubsnapshots.empty
        snapshot.absolutePath == fileSystemRoot
        snapshot.name == ""
        0 * _
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
        // root
        //   - a.txt <-- rootTextFile
        //   - a
        //     - b
        //       - c.html
        //       - c.txt <-- nestedTextFile
        //     - c
        //       - c.txt  <-- nestedSiblingTextFile
        //   - subdir1
        //     - a/b/c.html

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patterns), [:], unfilteredSubSnapshotsCollector)

        then:
        !unfilteredSubsnapshots.contains(snapshot)
        0 * _

        when:
        def visited = SnapshotVisitorUtil.getAbsolutePaths(snapshot, true)
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot, true)

        then:
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        !visited.contains(notTextFile.absolutePath)
        !visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == [
            '',
            'a',
            'a/b', 'a/b/c.txt',
            'a/c', 'a/c/c.txt',
            'a.txt'
        ] as Set

        unfilteredSubsnapshots*.absolutePath == [
            nestedTextFile,
            nestedSiblingTextFile.parentFile,
            rootTextFile
        ]*.absolutePath
    }

    def "should reuse existing file and directory snapshots"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("root.txt").createFile()
        def knownTextFile = rootDir.file("a/b/known.txt").createFile()
        def knownDir = rootDir.file("a/known").createDir()
        def previouslyUnknownFile = knownDir.file("previously-unknown.txt").createFile()
        // root
        //   - root.txt <-- rootTextFile
        //   - a
        //     - b
        //       - known.txt <-- knownFile
        //     - known <-- knownDir
        //       - previously-unknown.txt <-- previouslyUnknownFile
        def knownFileSnapshot = new RegularFileSnapshot(knownTextFile.absolutePath, knownTextFile.name, TestHashCodes.hashCodeFrom(1234), Stub(FileMetadata))
        def knownDirSnapshot = new DirectorySnapshot(knownDir.absolutePath, knownDir.name, AccessType.DIRECT, TestHashCodes.hashCodeFrom(5678), [])
        Map<String, FileSystemLocationSnapshot> knownSnapshots = [knownFileSnapshot, knownDirSnapshot].collectEntries { [it.absolutePath, it] }

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, knownSnapshots, unfilteredSubSnapshotsCollector)

        then:
        0 * _

        when:
        def visited = SnapshotUtil.indexByAbsolutePath(snapshot)

        then:
        visited[rootDir.absolutePath] instanceof DirectorySnapshot
        visited[rootTextFile.absolutePath] instanceof RegularFileSnapshot
        visited[knownTextFile.absolutePath].is(knownFileSnapshot)
        visited[knownDir.absolutePath].is(knownDirSnapshot)
        visited[previouslyUnknownFile.absolutePath] == null
    }

    def "should reuse existing file and directory snapshots when using a filter"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def knownDir = rootDir.file("known").createDir()
        def knownTextFile = rootDir.file("known/known.txt").createFile()
        def knownHtmlFile = rootDir.file("known/known.html").createFile()
        // root
        //   - known <-- knownDir
        //     - known.txt <-- knownTextFile
        //     - known.html <-- knownHtmlFile
        def knownTextFileSnapshot = new RegularFileSnapshot(knownTextFile.absolutePath, knownTextFile.name, TestHashCodes.hashCodeFrom(1234), Stub(FileMetadata))
        def knownHtmlFileSnapshot = new RegularFileSnapshot(knownHtmlFile.absolutePath, knownHtmlFile.name, TestHashCodes.hashCodeFrom(4321), Stub(FileMetadata))
        def knownDirSnapshot = new DirectorySnapshot(knownDir.absolutePath, knownDir.name, AccessType.DIRECT, TestHashCodes.hashCodeFrom(5678), [knownTextFileSnapshot, knownHtmlFileSnapshot])
        def knownSnapshots = [(knownDir.absolutePath): knownDirSnapshot]

        def patterns = new PatternSet()
        patterns.include("**/*.txt")

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patterns), knownSnapshots, unfilteredSubSnapshotsCollector)

        then:
        0 * _

        when:
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot, true)

        then:
        relativePaths as Set == [
            '',
            'known',
            'known/known.txt'
        ] as Set
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked directories in tree are marked as accessed via symlink"() {
        def rootDir = tmpDir.createDir("root")
        def linkTarget = tmpDir.createDir("linkTarget")
        linkTarget.file("other/text.txt").text = "text"
        def symlink = rootDir.file("some/sub/dir")
        symlink.createLink(linkTarget)

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector) as DirectorySnapshot
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        relativePaths == ["some", "some/sub", "some/sub/dir", "some/sub/dir/other", "some/sub/dir/other/text.txt"]
        SnapshotVisitorUtil.getAbsolutePaths(snapshot) == relativePaths.collect { new File(rootDir, it).absolutePath }
        def symlinkedDir = snapshot.children[0].children[0].children[0] as DirectorySnapshot
        symlinkedDir.accessType == AccessType.VIA_SYMLINK
        symlinkedDir.absolutePath == symlink.absolutePath
        unfilteredSubsnapshots == [snapshot]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked directories are snapshot correctly"() {
        def rootDir = tmpDir.file("root")
        def linkTarget = tmpDir.createDir("linkTarget")
        linkTarget.file("sub/text.txt").text = "text"
        rootDir.createLink(linkTarget)

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector) as DirectorySnapshot
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        relativePaths == ["sub", "sub/text.txt"]
        SnapshotVisitorUtil.getAbsolutePaths(snapshot) == relativePaths.collect { new File(rootDir, it).absolutePath }
        snapshot.accessType == AccessType.VIA_SYMLINK
        snapshot.absolutePath == rootDir.absolutePath
        unfilteredSubsnapshots == [snapshot]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "symlinked directories and files can be filtered correctly"() {
        def rootDir = tmpDir.file("root")
        def linkTarget1 = tmpDir.createDir("linkTarget1")
        linkTarget1.createFile("included/text.txt")
        linkTarget1.createFile("excluded/text.txt")
        linkTarget1.createFile("excluded/excluded.png")
        def linkTarget2 = tmpDir.createDir("linkTarget2")
        linkTarget2.createFile("included.txt")
        linkTarget2.createFile("excluded.png")
        def linkTarget3 = tmpDir.createFile("linkTarget3")

        rootDir.createLink(linkTarget1)
        linkTarget1.file("includedSymlink").createLink(linkTarget2)
        linkTarget1.file("excludedSymlink").createLink(linkTarget2)
        linkTarget2.file("symlinkedFile.txt").createLink(linkTarget3)
        linkTarget2.file("excludedSymlinkedFile.png").createLink(linkTarget3)

        // rootDir -> linkTarget1
        //   - included
        //     - text.txt
        //   - excluded
        //     - text.txt
        //     - excluded.png
        //   - includedSymlink -> linkTarget2
        //     - included.txt
        //     - excluded.png
        //     - symlinkedFile.txt -> linkTarget3 (file)
        //     - excludedSymlinkedFile.png -> linkTarget3 (file)
        //   - excludedSymlink -> linkTarget2
        //     - included.txt
        //     - excluded.png
        //     - symlinkedFile.txt -> linkTarget3 (file)
        //     - excludedSymlinkedFile.png -> linkTarget3 (file)

        def patterns = new PatternSet()
        patterns.include("included*/*.txt")

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patterns), [:], unfilteredSubSnapshotsCollector) as DirectorySnapshot
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        relativePaths == ["included", "included/text.txt", "includedSymlink", "includedSymlink/included.txt", "includedSymlink/symlinkedFile.txt"]
        SnapshotVisitorUtil.getAbsolutePaths(snapshot) == relativePaths.collect { new File(rootDir, it).absolutePath }
        unfilteredSubsnapshots*.absolutePath == absolutePathsFor(rootDir,
            "includedSymlink/included.txt",
            "includedSymlink/symlinkedFile.txt",
            "included")
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "parent directories of filtered symlinked directories are marked as filtered"() {
        def rootDir = tmpDir.createDir("root")
        rootDir.createFile("included/text.txt")
        def linkTarget = tmpDir.createDir("linkTarget2")
        linkTarget.createFile("included.txt")
        linkTarget.createFile("excluded.png")

        rootDir.file("includedSymlink").createLink(linkTarget)

        // rootDir
        //   - included
        //     - text.txt
        //   - includedSymlink -> linkTarget
        //     - included.txt
        //     - excluded.png

        def patterns = new PatternSet()
        patterns.include("included*/*.txt")

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patterns), [:], unfilteredSubSnapshotsCollector) as DirectorySnapshot
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        relativePaths == ["included", "included/text.txt", "includedSymlink", "includedSymlink/included.txt"]
        SnapshotVisitorUtil.getAbsolutePaths(snapshot) == relativePaths.collect { new File(rootDir, it).absolutePath }
        unfilteredSubsnapshots*.absolutePath == absolutePathsFor(rootDir, "includedSymlink/included.txt", "included")
    }

    List<String> absolutePathsFor(File rootDir, String... relativePaths) {
        // use "new File()" since TestFile canonicalizes the paths.
        return relativePaths.collect { new File(rootDir, it).absolutePath }
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "can snapshot symlinked directories and files within another"() {
        def rootDir = tmpDir.file("root")
        def linkTarget1 = tmpDir.createDir("linkTarget1")
        linkTarget1.file("in1/text.txt").text = "text"
        def linkTarget2 = tmpDir.createDir("linkTarget2")
        linkTarget2.createFile("in2/some/dir/file.txt")
        def linkTarget3 = tmpDir.createDir("linkTarget3")
        linkTarget3.createFile("in3/my/file.txt")
        def linkTarget4 = tmpDir.createFile("linkTarget4")
        rootDir.createLink(linkTarget1)
        linkTarget1.file("in1/linked").createLink(linkTarget2)
        linkTarget2.file("other/linked3").createLink(linkTarget3)
        linkTarget3.file("another/fileLink").createLink(linkTarget4)

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector) as DirectorySnapshot
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        relativePaths == [
            "in1", "in1/linked",
            "in1/linked/in2", "in1/linked/in2/some", "in1/linked/in2/some/dir", "in1/linked/in2/some/dir/file.txt",
            "in1/linked/other", "in1/linked/other/linked3",
            "in1/linked/other/linked3/another", "in1/linked/other/linked3/another/fileLink",
            "in1/linked/other/linked3/in3", "in1/linked/other/linked3/in3/my", "in1/linked/other/linked3/in3/my/file.txt",
            "in1/text.txt"
        ]
        SnapshotVisitorUtil.getAbsolutePaths(snapshot) == relativePaths.collect { new File(rootDir, it).absolutePath }
        snapshot.accessType == AccessType.VIA_SYMLINK
        snapshot.absolutePath == rootDir.absolutePath

        def link2 = snapshot.children[0].children[0] as DirectorySnapshot
        link2.accessType == AccessType.VIA_SYMLINK
        link2.name == "linked"

        def link3 = link2.children[1].children[0] as DirectorySnapshot
        link3.accessType == AccessType.VIA_SYMLINK
        link3.name == "linked3"

        def link4 = link3.children[0].children[0] as RegularFileSnapshot
        link4.accessType == AccessType.VIA_SYMLINK
        link4.name == "fileLink"

        unfilteredSubsnapshots == [snapshot]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "broken symlinks are snapshotted as missing"() {
        def rootDir = tmpDir.createDir("root")
        rootDir.file('brokenSymlink').createLink("linkTarget")
        assert rootDir.listFiles()*.exists() == [false]

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        snapshot.children.size() == 1
        def brokenSymlinkSnapshot = snapshot.children[0]
        brokenSymlinkSnapshot.class == MissingFileSnapshot
        brokenSymlinkSnapshot.accessType == AccessType.VIA_SYMLINK
        SnapshotVisitorUtil.getRelativePaths(snapshot) == ["brokenSymlink"]
        unfilteredSubsnapshots == [snapshot]
        0 * _

        when:
        rootDir.file("linkTarget").createFile() // unbreak my heart
        and:
        unfilteredSubsnapshots.clear()
        snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        snapshot.children*.class == [RegularFileSnapshot, RegularFileSnapshot]
        snapshot.children*.accessType == [AccessType.VIA_SYMLINK, AccessType.DIRECT]
        unfilteredSubsnapshots == [snapshot]
        0 * _
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "can snapshot a directory with cycles introduced via symlinks"() {
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.file("dir").createDir()
        dir.file('subdir').createLink(dir)

        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        snapshot.children.size() == 1
        def dirSnapshot = snapshot.children[0]
        dirSnapshot.class == DirectorySnapshot
        dirSnapshot.accessType == AccessType.DIRECT
        dirSnapshot.children == []
        unfilteredSubsnapshots == [snapshot]
        0 * _
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "can snapshot a directory with symlink cycle inside"() {
        def rootDir = tmpDir.createDir("root")
        def first = rootDir.file("first")
        def second = rootDir.file("second")
        def third = rootDir.file("third")
        first.createLink(second)
        second.createLink(third)
        third.createLink(first)
        when:
        def snapshot = directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        snapshot.children.size() == 3
        snapshot.children.every { it.class == MissingFileSnapshot }
        snapshot.children.every { it.accessType == AccessType.VIA_SYMLINK }
        unfilteredSubsnapshots == [snapshot]
        0 * _
    }

    @Requires(UnitTestPreconditions.FilePermissions)
    def "snapshotting unreadable #type fails"() {
        given:
        def rootDir = tmpDir.createDir("root")
        rootDir.file('readableFile').createFile()
        rootDir.file('readableDirectory').createDir()

        def unreadable = rootDir.file('unreadable')
        unreadable."create${type.capitalize()}"().makeUnreadable()

        when:
        directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        def ex = thrown(UncheckedIOException)
        ex.message == String.format(message, unreadable.absolutePath)
        0 * _

        cleanup:
        rootDir.listFiles()*.makeReadable()

        where:
        type   | message
        "dir"  | "java.nio.file.AccessDeniedException: %s"
        "file" | "java.io.FileNotFoundException: %s (Permission denied)"
    }


    @Requires(UnitTestPreconditions.UnixDerivative)
    @Issue("https://github.com/gradle/gradle/issues/2552")
    def "snapshotting named pipe fails"() {
        def rootDir = tmpDir.createDir("root")
        def pipe = rootDir.file("testPipe").createNamedPipe()

        when:
        directorySnapshotter.snapshot(rootDir.absolutePath, null, [:], unfilteredSubSnapshotsCollector)

        then:
        def ex = thrown(UncheckedIOException)
        ex.message == "java.io.IOException: Cannot snapshot ${pipe.absolutePath}: not a regular file"
        0 * _

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
