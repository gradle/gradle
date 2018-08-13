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
import org.gradle.api.internal.file.copy.FileCopier
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.AbstractTestForPatternSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import org.jmock.Expectations
import org.jmock.integration.junit4.JUnit4Mockery
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting
import static org.gradle.api.file.FileVisitorUtil.assertVisits
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory
import static org.gradle.api.internal.file.TestFiles.getPatternSetFactory
import static org.gradle.api.internal.file.TestFiles.resolver
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes
import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.instanceOf
import static org.hamcrest.Matchers.notNullValue
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class DefaultConfigurableFileTreeTest extends AbstractTestForPatternSet {
    JUnit4Mockery context = new JUnit4GroovyMockery()
    TaskResolver taskResolverStub = context.mock(TaskResolver)
    DefaultConfigurableFileTree fileSet
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    File testDir = tmpDir.testDirectory
    FileResolver fileResolverStub = resolver(testDir)
    FileCopier fileCopier

    PatternFilterable getPatternSet() {
        return fileSet
    }

    @Before
    void setUp() {
        super.setUp()
        NativeServicesTestFixture.initialize()
        fileSet = new DefaultConfigurableFileTree(testDir, fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())
        fileCopier = new FileCopier(DirectInstantiator.INSTANCE, fileResolverStub, context.mock(FileLookup), directoryFileTreeFactory())
    }

    @Test
    void testFileSetConstructionWithBaseDir() {
        assertEquals(testDir, fileSet.dir)
    }

    @Test
    void testFileSetConstructionFromMap() {
        fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: testDir, includes: ['include'], builtBy: ['a'], fileCopier, directoryFileTreeFactory())
        assertEquals(testDir, fileSet.dir)
        assertEquals(['include'] as Set, fileSet.includes)
        assertEquals(['a'] as Set, fileSet.builtBy)
    }

    @Test(expected = InvalidUserDataException)
    void testFileSetConstructionWithNoBaseDirSpecified() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree([:], fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())
        fileSet.contains(new File('unknown'))
    }

    @Test
    void testFileSetConstructionWithBaseDirAsString() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: 'dirname', fileCopier, directoryFileTreeFactory())
        assertEquals(tmpDir.file("dirname"), fileSet.dir);
    }

    @Test
    void testResolveAddsADirectoryFileTree() {
        FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext)

        context.checking {
            one(resolveContext).add(withParam(notNullValue()))
            will { fileTree ->
                assertThat(fileTree, instanceOf(DirectoryFileTree))
                assertThat(fileTree.dir, equalTo(testDir))
            }
        }

        fileSet.visitContents(resolveContext)
    }

    @Test
    void testResolveAddsBuildDependenciesIfNotEmpty() {
        FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext)
        fileSet.builtBy("classes")

        context.checking {
            one(resolveContext).add(withParam(instanceOf(DirectoryFileTree)))
            one(resolveContext).add(withParam(instanceOf(TaskDependency)))
        }

        fileSet.visitContents(resolveContext)
    }

    @Test
    void testCanScanForFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertThat(fileSet.files, equalTo([included1, included2] as Set))
    }

    @Test
    void testCanVisitFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
    }

    @Test
    void testCanStopVisitingFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir/otherDir/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertCanStopVisiting(fileSet)
    }

    @Test
    void testContainsFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertTrue(fileSet.contains(included1))
        assertTrue(fileSet.contains(included2))
        assertFalse(fileSet.contains(testDir))
        assertFalse(fileSet.contains(included1.parentFile))
        assertFalse(fileSet.contains(included2.parentFile))
        assertFalse(fileSet.contains(new File(testDir, 'does not exist')))
        assertFalse(fileSet.contains(testDir.parentFile))
        assertFalse(fileSet.contains(new File('something')))
    }

    @Test
    void testCanAddToAntTask() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
    }

    @Test
    void testIsEmptyWhenBaseDirDoesNotExist() {
        fileSet.dir = new File(testDir, 'does not exist')

        assertThat(fileSet.files, isEmpty())
        assertSetContainsForAllTypes(fileSet)
        assertVisits(fileSet, [], [])
    }

    @Test
    void testCanSelectFilesUsingPatterns() {
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

        assertThat(fileSet.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(fileSet.contains(included1))
        assertFalse(fileSet.contains(excluded1))
        assertFalse(fileSet.contains(ignored1))
    }

    @Test
    void testCanFilterMatchingFilesUsingConfigureClosure() {
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

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }

    @Test
    void testCanFilterMatchingFilesUsingPatternSet() {
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

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }

    @Test
    void testCanFilterAndSelectFiles() {
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

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }

    @Test
    void testDisplayName() {
        assertThat(fileSet.displayName, equalTo("directory '$testDir'".toString()))
    }

    @Test
    void canGetAndSetTaskDependencies() {
        FileResolver fileResolverStub = context.mock(FileResolver.class);
        context.checking {
            addGetPatternSetFactory(delegate, fileResolverStub)
        }
        fileSet = new DefaultConfigurableFileTree(testDir, fileResolverStub, taskResolverStub, fileCopier, directoryFileTreeFactory())

        assertThat(fileSet.getBuiltBy(), isEmpty());

        fileSet.builtBy("a");
        fileSet.builtBy("b");
        fileSet.from("f");

        assertThat(fileSet.getBuiltBy(), equalTo(WrapUtil.toSet((Object) "a", "b")));

        fileSet.setBuiltBy(WrapUtil.toList("c"));

        assertThat(fileSet.getBuiltBy(), equalTo(WrapUtil.toSet((Object) "c")));
        final Task task = context.mock(Task.class);
        context.checking {
            allowing(fileResolverStub).resolve("f");
            will(returnValue(new File("f")));
            allowing(taskResolverStub).resolveTask('c');
            will(returnValue(task));
        }

        assertThat(fileSet.getBuildDependencies().getDependencies(null), equalTo((Set) WrapUtil.toSet(task)));
    }

    static void addGetPatternSetFactory(Expectations expectations, FileResolver resolverMock) {
        expectations.allowing(resolverMock).getPatternSetFactory();
        expectations.will(expectations.returnValue(getPatternSetFactory()));
    }
}
