/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.file.collections

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.copy.FileCopier
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.AbstractTestForPatternSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting
import static org.gradle.api.file.FileVisitorUtil.assertVisits
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory
import static org.gradle.api.internal.file.TestFiles.resolver
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes

class DefaultConfigurableFileTreeTest extends AbstractTestForPatternSet {
    TaskResolver taskResolverStub = Mock(TaskResolver)
    DefaultConfigurableFileTree fileSet
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    File testDir = tmpDir.testDirectory
    def fileLookup = Mock(FileLookup)
    FileResolver fileResolverStub = resolver(testDir)
    FileCopier fileCopier

    PatternFilterable getPatternSet() {
        return fileSet
    }

    void setup() {
        NativeServicesTestFixture.initialize()
        fileSet = new DefaultConfigurableFileTree(testDir, fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())
        fileCopier = new FileCopier(DirectInstantiator.INSTANCE, fileResolverStub, fileLookup, directoryFileTreeFactory())
    }

    def testFileSetConstructionWithBaseDir() {
        expect:
        testDir == fileSet.dir
    }

    def testFileSetConstructionFromMap() {
        fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: testDir, includes: ['include'], builtBy: ['a'], fileCopier, directoryFileTreeFactory())

        expect:
        testDir == fileSet.dir
        ['include'] as Set == fileSet.includes
        ['a'] as Set == fileSet.builtBy
    }

    def testFileSetConstructionWithNoBaseDirSpecified() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree([:], fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())

        when:
        fileSet.contains(new File('unknown'))
        then:
        thrown(InvalidUserDataException)
    }

    def testFileSetConstructionWithBaseDirAsString() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: 'dirname', fileCopier, directoryFileTreeFactory())

        expect:
        tmpDir.file("dirname") == fileSet.dir
    }

    def testResolveAddsADirectoryFileTree() {
        def resolveContext = Mock(FileCollectionResolveContext)

        when:
        fileSet.visitContents(resolveContext)
        then:
        1 * resolveContext.add({ it != null }) >> { args ->
            def fileTree = args[0]
            assert fileTree instanceof DirectoryFileTree
            assert fileTree.dir == testDir
        }
        0 * _
    }

    def testResolveAddsBuildDependenciesIfNotEmpty() {
        def resolveContext = Mock(TaskDependencyResolveContext)
        fileSet.builtBy("classes")

        when:
        fileSet.visitDependencies(resolveContext)
        then:
        1 * resolveContext.add({ it instanceof TaskDependency})
        0 * _
    }

    def testCanScanForFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        fileSet.files == [included1, included2] as Set
    }

    def testCanVisitFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
    }

    def testCanStopVisitingFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir/otherDir/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertCanStopVisiting(fileSet)
    }

    def testContainsFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        fileSet.contains(included1)
        fileSet.contains(included2)
        !fileSet.contains(testDir)
        !fileSet.contains(included1.parentFile)
        !fileSet.contains(included2.parentFile)
        !fileSet.contains(new File(testDir, 'does not exist'))
        !fileSet.contains(testDir.parentFile)
        !fileSet.contains(new File('something'))
    }

    def testCanAddToAntTask() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
    }

    def testIsEmptyWhenBaseDirDoesNotExist() {
        fileSet.dir = new File(testDir, 'does not exist')

        expect:
        fileSet.files.empty
        assertSetContainsForAllTypes(fileSet)
        assertVisits(fileSet, [], [])
    }

    def testCanSelectFilesUsingPatterns() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileSet.include('*/*included*')
        fileSet.exclude('**/not*')

        expect:
        fileSet.files == [included1, included2] as Set
        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        fileSet.contains(included1)
        !fileSet.contains(excluded1)
        !fileSet.contains(ignored1)
    }

    def testCanFilterMatchingFilesUsingConfigureClosure() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        FileTree filtered = fileSet.matching {
            include('*/*included*')
            exclude('**/not*')
        }

        expect:
        filtered.files == [included1, included2] as Set
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        filtered.contains(included1)
        !filtered.contains(excluded1)
        !filtered.contains(ignored1)
    }

    def testCanFilterMatchingFilesUsingPatternSet() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        PatternSet patternSet = new PatternSet(includes: ['*/*included*'], excludes: ['**/not*'])
        FileTree filtered = fileSet.matching(patternSet)

        expect:
        filtered.files == [included1, included2] as Set
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        filtered.contains(included1)
        !filtered.contains(excluded1)
        !filtered.contains(ignored1)
    }

    def testCanFilterAndSelectFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File excluded2 = new File(testDir, 'subDir/excluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, excluded2, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileSet.exclude '**/excluded*'

        FileTree filtered = fileSet.matching {
            include('*/*included*')
            exclude('**/not*')
        }

        expect:
        filtered.files == [included1, included2] as Set
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        filtered.contains(included1)
        !filtered.contains(excluded1)
        !filtered.contains(ignored1)
    }

    def testDisplayName() {
        expect:
        fileSet.displayName == "directory '$testDir'".toString()
    }

    def canGetAndSetTaskDependencies() {
        def fileResolverStub = Stub(FileResolver.class) {
            getPatternSetFactory() >> TestFiles.getPatternSetFactory()
        }
        fileSet = new DefaultConfigurableFileTree(testDir, fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())
        def task = Stub(Task)

        expect:
        fileSet.getBuiltBy().empty

        when:
        fileSet.builtBy("a")
        fileSet.builtBy("b")
        fileSet.from("f")
        then:
        fileSet.getBuiltBy() == ["a", "b"] as Set

        when:
        fileSet.setBuiltBy(["c"])
        then:
        fileSet.getBuiltBy() == ["c"] as Set

        when:
        def dependencies = fileSet.getBuildDependencies().getDependencies(null)
        then:
        1 * taskResolverStub.resolveTask('c') >> task
        0 * _

        dependencies == [task] as Set
    }
}
