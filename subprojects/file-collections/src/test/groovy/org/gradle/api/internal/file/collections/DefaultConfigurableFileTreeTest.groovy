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
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.AbstractTestForPatternSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting
import static org.gradle.api.file.FileVisitorUtil.assertVisits
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory
import static org.gradle.api.internal.file.TestFiles.resolver
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes

class DefaultConfigurableFileTreeTest extends AbstractTestForPatternSet {
    TaskDependencyFactory taskDependencyFactory = DefaultTaskDependencyFactory.withNoAssociatedProject()
    DefaultConfigurableFileTree fileSet
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    File testDir = tmpDir.testDirectory
    FileResolver fileResolverStub = resolver(testDir)

    PatternFilterable getPatternSet() {
        return fileSet
    }

    void setup() {
        fileSet = new DefaultConfigurableFileTree(fileResolverStub, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())
        fileSet.from(testDir)
    }

    def testFileSetConstructionWithBaseDir() {
        expect:
        testDir == fileSet.dir
    }

    def testFileSetConstructionWithNoBaseDirSpecified() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree(fileResolverStub, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())

        when:
        fileSet.contains(new File('unknown'))
        then:
        thrown(InvalidUserDataException)
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
        def context = Mock(TaskDependencyResolveContext)
        def dep = new DefaultTaskDependency()
        def taskDependencyFactory = Stub(TaskDependencyFactory)
        _ * taskDependencyFactory.configurableDependency() >> dep
        fileSet = new DefaultConfigurableFileTree(fileResolverStub, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())

        expect:
        fileSet.getBuiltBy().empty

        when:
        fileSet.builtBy("a")
        fileSet.builtBy("b")
        fileSet.from("f")
        then:
        fileSet.getBuiltBy() == ["a", "b"] as Set
        dep.mutableValues == ["a", "b"] as Set

        when:
        fileSet.setBuiltBy(["c"])
        then:
        fileSet.getBuiltBy() == ["c"] as Set
        dep.mutableValues == ["c"] as Set

        when:
        fileSet.visitDependencies(context)

        then:
        1 * context.add(dep)
    }
}
