/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.copy

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.Action
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock)
public class CopySpecImplTest {

    @Rule public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    private TestFile baseFile = testDir.testDirectory
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final FileResolver fileResolver = context.mock(FileResolver);
    private final CopySpecImpl spec = new CopySpecImpl(fileResolver)

    private List<String> getTestSourceFileNames() {
        ['first', 'second']
    }

    private List<File> getAbsoluteTestSources() {
        testSourceFileNames.collect { new File(baseFile, it) }
    }

    @Test public void testAbsoluteFromList() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources);
        assertEquals([sources], spec.sourcePaths as List);
    }

    @Test public void testFromArray() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources as File[]);
        assertEquals(sources, spec.sourcePaths as List);
    }

    @Test public void testSourceWithClosure() {
        CopySpecImpl child = spec.from('source') {
        }

        assertThat(child, not(sameInstance(spec)))
        assertEquals(['source'], child.sourcePaths as List);
    }

    @Test public void testMultipleSourcesWithClosure() {
        CopySpecImpl child = spec.from(['source1', 'source2']) {
        }

        assertThat(child, not(sameInstance(spec)))
        assertEquals(['source1', 'source2'], child.sourcePaths.flatten() as List);
    }

    @Test public void testDefaultDestinationPathForRootSpec() {
        assertThat(spec.destPath, equalTo(new RelativePath(false)))
    }

    @Test public void testInto() {
        spec.into 'spec'
        assertThat(spec.destPath, equalTo(new RelativePath(false, 'spec')))
        spec.into '/'
        assertThat(spec.destPath, equalTo(new RelativePath(false)))
    }

    @Test public void testIntoWithAClosure() {
        spec.into { 'spec' }
        assertThat(spec.destPath, equalTo(new RelativePath(false, 'spec')))
        spec.into { return { 'spec' } }
        assertThat(spec.destPath, equalTo(new RelativePath(false, 'spec')))
    }

    @Test public void testWithSpec() {
        CopySpecImpl other1 = new CopySpecImpl(fileResolver)
        CopySpecImpl other2 = new CopySpecImpl(fileResolver)

        spec.with other1, other2
        assertTrue(spec.sourcePaths.empty)
        assertThat(spec.childSpecs.size(), equalTo(2))
    }
    
    @Test public void testWithSpecSource() {
        CopyActionImpl source = new CopyActionImpl(fileResolver, null)

        spec.with source
        assertTrue(spec.sourcePaths.empty)
        assertThat(spec.childSpecs.size(), equalTo(1))
    }

    @Test public void testWithSpecInheritsDestinationPathFromParent() {
        CopySpecImpl other = new CopySpecImpl(fileResolver)
        other.into 'other'

        spec.into 'spec'
        spec.with other

        ReadableCopySpec child = spec.childSpecs[0]
        assertThat(child.destPath, equalTo(new RelativePath(false, 'spec', 'other')))
    }

    @Test public void testDestinationWithClosure() {
        CopySpecImpl child = spec.into('target') {
        }

        assertThat(child, not(sameInstance(spec)))
        assertThat(child.destPath, equalTo(new RelativePath(false, 'target')))
    }

    @Test public void testGetAllSpecsReturnsBreadthwiseTraverseOfSpecs() {
        CopySpecImpl child = spec.into('somedir') { }
        CopySpecImpl grandchild = child.into('somedir') { }
        CopySpecImpl child2 = spec.into('somedir') { }

        assertThat(spec.allSpecs, equalTo([spec, child, grandchild, child2]))
    }

    @Test public void testRootSpecHasRootPathAsDestination() {
        assertThat(spec.destPath, equalTo(new RelativePath(false)))
    }

    @Test public void testChildSpecResolvesIntoArgRelativeToParentDestinationDir() {
        CopySpecImpl child = spec.from('somedir') { into 'child' }
        assertThat(child.destPath, equalTo(new RelativePath(false, 'child')))

        CopySpecImpl grandchild = child.from('somedir') { into 'grandchild'}
        assertThat(grandchild.destPath, equalTo(new RelativePath(false, 'child', 'grandchild')))

        grandchild.into '/grandchild'
        assertThat(grandchild.destPath, equalTo(new RelativePath(false, 'grandchild')))
    }

    @Test public void testChildSpecUsesParentDestinationPathAsDefault() {
        CopySpecImpl child = spec.from('somedir') { }
        assertThat(child.destPath, equalTo(spec.destPath))

        child.into 'child'

        CopySpecImpl grandchild = child.from('somedir') { }
        assertThat(grandchild.destPath, equalTo(child.destPath))
    }

    @Test public void testSourceIsFilteredTreeOfSources() {
        spec.from 'a'
        spec.from 'b'

        def filteredTree = context.mock(FileTree, 'filtered')

        context.checking {
            one(fileResolver).resolveFilesAsTree(['a', 'b'] as Set)
            def tree = context.mock(FileTree, 'source')
            will(returnValue(tree))
            one(tree).matching(withParam(equalTo(spec.patternSet)))
            will(returnValue(filteredTree))
        }

        assertThat(spec.source, sameInstance(filteredTree))
    }

    @Test public void testChildUsesPatternsFromParent() {
        CopySpecImpl child = spec.from('dir') {}
        Spec specInclude = [:] as Spec
        Spec specExclude = [:] as Spec
        Spec childInclude = [:] as Spec
        Spec childExclude = [:] as Spec

        spec.include('parent-include')
        spec.exclude('parent-exclude')
        spec.include(specInclude)
        spec.exclude(specExclude)
        child.include('child-include')
        child.exclude('child-exclude')
        child.include(childInclude)
        child.exclude(childExclude)

        PatternSet patterns = child.patternSet
        assertThat(patterns.includes, equalTo(['parent-include', 'child-include'] as Set))
        assertThat(patterns.excludes, equalTo(['parent-exclude', 'child-exclude'] as Set))
        assertThat(patterns.includeSpecs, equalTo([specInclude, childInclude] as Set))
        assertThat(patterns.excludeSpecs, equalTo([specExclude, childExclude] as Set))
    }

    @Test public void testChildUsesParentPatternsAsDefault() {
        CopySpecImpl child = spec.from('dir') {}
        Spec specInclude = [:] as Spec
        Spec specExclude = [:] as Spec

        spec.include('parent-include')
        spec.exclude('parent-exclude')
        spec.include(specInclude)
        spec.exclude(specExclude)

        PatternSet patterns = child.patternSet
        assertThat(patterns.includes, equalTo(['parent-include'] as Set))
        assertThat(patterns.excludes, equalTo(['parent-exclude'] as Set))
        assertThat(patterns.includeSpecs, equalTo([specInclude] as Set))
        assertThat(patterns.excludeSpecs, equalTo([specExclude] as Set))
    }

    @Test public void testChildUsesCaseSensitiveFlagFromParentAsDefault() {
        CopySpecImpl child = spec.from('dir') {}
        assertTrue(child.caseSensitive)
        assertTrue(child.patternSet.caseSensitive)

        spec.caseSensitive = false
        assertFalse(child.caseSensitive)
        assertFalse(child.patternSet.caseSensitive)

        child.caseSensitive = true
        assertTrue(child.caseSensitive)
        assertTrue(child.patternSet.caseSensitive)
    }

    @Test public void testChildUsesIncludeEmptyDirsFlagFromParentAsDefault() {
        def child = spec.from('dir') {}
        assert child.includeEmptyDirs

        spec.includeEmptyDirs = false
        assert !child.includeEmptyDirs

        child.includeEmptyDirs = true
        assert child.includeEmptyDirs
    }

    @Test public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.allCopyActions.size(), equalTo(1))
    }

    @Test public void testArgFilter() {
        spec.filter(HeadFilter, lines: 15, skip: 2)
        assertThat(spec.allCopyActions.size(), equalTo(1))
    }

    @Test public void testExpand() {
        spec.expand(version: '1.2', skip: 2)
        assertThat(spec.allCopyActions.size(), equalTo(1))
    }

    @Test public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines: 15, skip: 2)

        assertThat(spec.allCopyActions.size(), equalTo(2))
    }

    @Test public void testAddsStringNameTransformerToActions() {
        spec.rename("regexp", "replacement")

        assertThat(spec.allCopyActions.size(), equalTo(1))
        assertThat(spec.allCopyActions[0], instanceOf(RenamingCopyAction))
        assertThat(spec.allCopyActions[0].transformer, instanceOf(RegExpNameMapper))
    }

    @Test public void testAddsPatternNameTransformerToActions() {
        spec.rename(/regexp/, "replacement")

        assertThat(spec.allCopyActions.size(), equalTo(1))
        assertThat(spec.allCopyActions[0], instanceOf(RenamingCopyAction))
        assertThat(spec.allCopyActions[0].transformer, instanceOf(RegExpNameMapper))
    }

    @Test public void testAddsClosureToActions() {
        spec.rename {}

        assertThat(spec.allCopyActions.size(), equalTo(1))
        assertThat(spec.allCopyActions[0], instanceOf(RenamingCopyAction))
    }

    @Test public void testAddAction() {
        def action = context.mock(Action)
        spec.eachFile(action)

        assertThat(spec.allCopyActions, equalTo([action]))
    }

    @Test public void testAddActionAsClosure() {
        def action = {}
        spec.eachFile(action)

        assertThat(spec.allCopyActions.size(), equalTo(1))
    }

    @Test public void testSpecInheritsActionsFromParent() {
        Action parentAction = context.mock(Action, 'parent')
        Action childAction = context.mock(Action, 'child')

        spec.eachFile parentAction
        CopySpecImpl childSpec = spec.from('src') {
            eachFile childAction
        }

        assertThat(childSpec.allCopyActions, equalTo([parentAction, childAction]))
    }

    @Test public void testHasNoPermissionsByDefault() {
        assert spec.fileMode == null
        assert spec.dirMode == null
    }

    @Test public void testInheritsPermissionsFromParent() {
        spec.fileMode = 0x1
        spec.dirMode = 0x2

        CopySpecImpl child = spec.from('src') { }
        org.junit.Assert.assertEquals(0x1, child.fileMode)
        org.junit.Assert.assertEquals(0x2, child.dirMode)
    }

    @Test public void testHasNoSourceByDefault() {
        assertFalse(spec.hasSource())
    }

    @Test public void testHasSourceWhenSpecHasSource() {
        spec.from 'source'
        assertTrue(spec.hasSource())
    }

    @Test public void testHasSourceWhenChildSpecHasSource() {
        spec.from('source') {}
        assertTrue(spec.hasSource())
    }

    @Test public void testGetSetDuplicatesStrategy() {
        assertEquals(DuplicatesStrategy.inherit, spec.duplicatesStrategy)
        spec.duplicatesStrategy = 'include'
        assertEquals(DuplicatesStrategy.include, spec.duplicatesStrategy)
        spec.duplicatesStrategy = 'exclude'
        assertEquals(DuplicatesStrategy.exclude, spec.duplicatesStrategy)
        spec.duplicatesStrategy = null
        assertEquals(null, spec.duplicatesStrategy)
        spec.duplicatesStrategy = 'inherit'
        assertEquals(DuplicatesStrategy.inherit, spec.duplicatesStrategy)
    }


    @Test public void testDuplicatesStrategyInherited() {
        spec.duplicatesStrategy = 'exclude'
        spec.with new CopySpecImpl(fileResolver, spec)
        assertEquals(DuplicatesStrategy.exclude, spec.childSpecs[0].duplicatesStrategy)
    }

    @Test public void testMatchingCreatesAppropriateAction() {
        spec.matching("root/**/a*") {
        }
        assertEquals(1, spec.allCopyActions.size())
        assertThat(spec.allCopyActions[0], instanceOf(MatchingCopyAction))

        Spec<RelativePath> matchSpec = spec.allCopyActions[0].matchSpec
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/folder/abc')))
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/abc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/notRoot/abc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/root/bbc')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, '/notRoot/bbc')))
    }

    @Test public void testNotMatchingCreatesAppropriateAction() {
        // no path component starting with an a
        spec.notMatching("**/a*/**") {
        }
        assertEquals(1, spec.allCopyActions.size())
        assertThat(spec.allCopyActions[0], instanceOf(MatchingCopyAction))

        Spec<RelativePath> matchSpec = spec.allCopyActions[0].matchSpec
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/folder1/folder2')))
        assertTrue(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'modules/project1')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'archives/folder/file')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/archives/file')))
        assertFalse(matchSpec.isSatisfiedBy(RelativePath.parse(true, 'root/folder/abc')))
    }

}

