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
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.util.AbstractTestForPatternSet
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import org.jmock.integration.junit4.JUnit4Mockery
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting
import static org.gradle.api.file.FileVisitorUtil.assertVisits
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes
import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class DefaultConfigurableFileTreeTest extends AbstractTestForPatternSet {
    JUnit4Mockery context = new JUnit4GroovyMockery();
    TaskResolver taskResolverStub = context.mock(TaskResolver.class);
    Instantiator instantiator = context.mock(Instantiator)
    DefaultConfigurableFileTree fileSet
    FileResolver fileResolverStub = [resolve: {it as File}] as FileResolver
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    File testDir = tmpDir.testDirectory

    PatternFilterable getPatternSet() {
        return fileSet
    }

    Class getPatternSetType() {
        DefaultConfigurableFileTree
    }

    @Before public void setUp() {
        super.setUp()
        fileSet = patternSetType.newInstance(testDir, fileResolverStub, taskResolverStub, instantiator)
    }

    @Test public void testFileSetConstructionWithBaseDir() {
        fileSet = new DefaultConfigurableFileTree(testDir, fileResolverStub, taskResolverStub, instantiator)
        assertEquals(testDir, fileSet.dir)
    }

    @Test public void testFileSetConstructionFromMap() {
        fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: testDir, includes: ['include'], instantiator)
        assertEquals(testDir, fileSet.dir)
        assertEquals(['include'] as Set, fileSet.includes)
    }

    @Test(expected = InvalidUserDataException) public void testFileSetConstructionWithNoBaseDirSpecified() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree([:], fileResolverStub, taskResolverStub, instantiator)
        fileSet.contains(new File('unknown'))
    }

    @Test public void testFileSetConstructionWithBaseDirAsString() {
        DefaultConfigurableFileTree fileSet = new DefaultConfigurableFileTree(fileResolverStub, taskResolverStub, dir: 'dirname', instantiator)
        assertEquals(new File('dirname'), fileSet.dir);
    }

    @Test public void testResolveAddsADirectoryFileTree() {
        FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext)

        context.checking {
            one(resolveContext).add(withParam(notNullValue()))
            will { fileTree ->
                assertThat(fileTree, instanceOf(DirectoryFileTree))
                assertThat(fileTree.dir, equalTo(testDir))
            }
        }

        fileSet.resolve(resolveContext)
    }

    @Test public void testResolveAddsBuildDependenciesIfNotEmpty() {
        FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext)
        fileSet.builtBy("classes")

        context.checking {
            one(resolveContext).add(withParam(instanceOf(DirectoryFileTree)))
            one(resolveContext).add(withParam(instanceOf(TaskDependency)))
        }

        fileSet.resolve(resolveContext)
    }
    
    @Test public void testCanScanForFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertThat(fileSet.files, equalTo([included1, included2] as Set))
    }

    @Test public void testCanVisitFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
    }

    @Test public void testCanStopVisitingFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir/otherDir/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertCanStopVisiting(fileSet)
    }

    @Test public void testContainsFiles() {
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

    @Test public void testCanAddToAntTask() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
    }

    @Test public void testIsEmptyWhenBaseDirDoesNotExist() {
        fileSet.dir = new File(testDir, 'does not exist')

        assertThat(fileSet.files, isEmpty())
        assertSetContainsForAllTypes(fileSet)
        assertVisits(fileSet, [], [])
    }

    @Test public void testCanSelectFilesUsingPatterns() {
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

    @Test public void testCanFilterMatchingFilesUsingConfigureClosure() {
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

    @Test public void testCanFilterMatchingFilesUsingPatternSet() {
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

    @Test public void testCanFilterAndSelectFiles() {
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

    @Test public void testDisplayName() {
        assertThat(fileSet.displayName, equalTo("directory '$testDir'".toString()))
    }

    @Test public void testStopExecutionIfEmptyWhenNoMatchingFilesFound() {
        fileSet.include('**/*included')
        new File(testDir, 'excluded').text = 'some text'

        try {
            fileSet.stopExecutionIfEmpty()
            fail()
        } catch (StopExecutionException e) {
            assertThat(e.message, equalTo("Directory '$testDir' does not contain any files." as String))
        }
    }

    @Test public void testStopExecutionIfEmptyWhenMatchingFilesFound() {
        fileSet.include('**/*included')
        new File(testDir, 'included').text = 'some text'

        fileSet.stopExecutionIfEmpty()
    }

    @Test
    public void canGetAndSetTaskDependencies() {
        FileResolver fileResolverStub = context.mock(FileResolver.class);
        fileSet = patternSetType.newInstance(testDir, fileResolverStub, taskResolverStub, instantiator)

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
}
