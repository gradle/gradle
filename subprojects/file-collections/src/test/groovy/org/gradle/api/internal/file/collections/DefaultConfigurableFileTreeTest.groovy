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
import org.gradle.api.internal.file.FileCollectionStructureVisitor
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
    DefaultConfigurableFileTree fileTree
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    File testDir = tmpDir.testDirectory
    FileResolver fileResolverStub = resolver(testDir)
    FileCollectionObservationListener listener = Mock()

    PatternFilterable getPatternSet() {
        return fileTree
    }

    void setup() {
        fileTree = new DefaultConfigurableFileTree(fileResolverStub, listener, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())
        fileTree.from(testDir)
    }

    def testFileSetConstructionWithBaseDir() {
        expect:
        testDir == fileTree.dir
    }

    def testFileSetConstructionWithNoBaseDirSpecified() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree(fileResolverStub, listener, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())

        when:
        fileSet.contains(new File('unknown'))
        then:
        thrown(InvalidUserDataException)
    }

    def testCanVisitStructure() {
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        fileTree.visitStructure(visitor)

        then:
        1 * visitor.startVisit(_, fileTree) >> true
        1 * visitor.startVisit(_, { it instanceof FileTreeAdapter }) >> true
        1 * visitor.visitFileTree(testDir, _, _)
        0 * _
    }

    def testResolveAddsBuildDependenciesIfNotEmpty() {
        def resolveContext = Mock(TaskDependencyResolveContext)
        fileTree.builtBy("classes")

        when:
        fileTree.visitDependencies(resolveContext)
        then:
        1 * resolveContext.add({ it instanceof TaskDependency })
        0 * _
    }

    def testCanScanForFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        when:
        def files = fileTree.files

        then:
        files == [included1, included2] as Set

        and:
        1 * listener.fileCollectionObserved(fileTree)
    }

    def testCanVisitFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertVisits(fileTree, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
    }

    def testNotifiesListenerWhenContentVisited() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        when:
        fileTree.visit { d -> }

        then:
        1 * listener.fileCollectionObserved(fileTree)
    }

    def testCanStopVisitingFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir/otherDir/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertCanStopVisiting(fileTree)
    }

    def testContainsFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        fileTree.contains(included1)
        fileTree.contains(included2)
        !fileTree.contains(testDir)
        !fileTree.contains(included1.parentFile)
        !fileTree.contains(included2.parentFile)
        !fileTree.contains(new File(testDir, 'does not exist'))
        !fileTree.contains(testDir.parentFile)
        !fileTree.contains(new File('something'))
    }

    def testNotifiesListenerWhenMembershipIsQueried() {
        when:
        def result = fileTree.contains(testDir)

        then:
        !result
        1 * listener.fileCollectionObserved(fileTree)
    }

    def testCanAddToAntTask() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        expect:
        assertSetContainsForAllTypes(fileTree, 'subDir/included1', 'subDir2/included2')
    }

    def testIsEmptyWhenBaseDirDoesNotExist() {
        fileTree.dir = new File(testDir, 'does not exist')

        expect:
        fileTree.files.empty
        fileTree.empty
        assertSetContainsForAllTypes(fileTree)
        assertVisits(fileTree, [], [])
    }

    def testNotifiesListenerWhenIsEmptyQueried() {
        when:
        def result = fileTree.empty

        then:
        result
        1 * listener.fileCollectionObserved(fileTree)
    }

    def testCanSelectFilesUsingPatterns() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileTree.include('*/*included*')
        fileTree.exclude('**/not*')

        expect:
        fileTree.files == [included1, included2] as Set
        assertSetContainsForAllTypes(fileTree, 'subDir/included1', 'subDir2/included2')
        assertVisits(fileTree, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        fileTree.contains(included1)
        !fileTree.contains(excluded1)
        !fileTree.contains(ignored1)
    }

    def testCanFilterMatchingFilesUsingConfigureClosure() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        FileTree filtered = fileTree.matching {
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
        [included1, included2, excluded1, ignored1].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        PatternSet patternSet = new PatternSet(includes: ['*/*included*'], excludes: ['**/not*'])
        FileTree filtered = fileTree.matching(patternSet)

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
        [included1, included2, excluded1, excluded2, ignored1].each { File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileTree.exclude '**/excluded*'

        FileTree filtered = fileTree.matching {
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
        fileTree.displayName == "directory '$testDir'".toString()
    }

    def canGetAndSetTaskDependencies() {
        def context = Mock(TaskDependencyResolveContext)
        def dep = new DefaultTaskDependency()
        def taskDependencyFactory = Stub(TaskDependencyFactory)
        _ * taskDependencyFactory.configurableDependency() >> dep
        fileTree = new DefaultConfigurableFileTree(fileResolverStub, listener, TestFiles.patternSetFactory, taskDependencyFactory, directoryFileTreeFactory())

        expect:
        fileTree.getBuiltBy().empty

        when:
        fileTree.builtBy("a")
        fileTree.builtBy("b")
        fileTree.from("f")
        then:
        fileTree.getBuiltBy() == ["a", "b"] as Set
        dep.mutableValues == ["a", "b"] as Set

        when:
        fileTree.setBuiltBy(["c"])
        then:
        fileTree.getBuiltBy() == ["c"] as Set
        dep.mutableValues == ["c"] as Set

        when:
        fileTree.visitDependencies(context)

        then:
        1 * context.add(dep)
    }
}
