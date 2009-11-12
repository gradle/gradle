package org.gradle.api.internal.file

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.file.RelativePath
import org.gradle.integtests.TestFile
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.apache.tools.zip.UnixStat
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.file.FileTree

@RunWith(JMock)
public class CopySpecImplTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    private TestFile baseFile = testDir.dir
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

    @Test public void testFromSpec() {
        CopySpecImpl other = new CopySpecImpl(fileResolver)
        spec.from other
        assertTrue(spec.sourcePaths.empty)
        assertThat(spec.childSpecs.size(), equalTo(1))
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
    
    @Test public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.filterChain.getLastFilter(), instanceOf(StripJavaComments))
    }

    @Test public void testArgFilter() {
        spec.filter(HeadFilter, lines: 15, skip: 2)

        org.apache.tools.ant.filters.HeadFilter filter = spec.filterChain.getLastFilter()
        assertThat(filter, instanceOf(HeadFilter))
        assertEquals(15, filter.lines)
        assertEquals(2, filter.skip)
    }

    @Test public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines: 15, skip: 2)

        assertThat(spec.filterChain.getLastFilter(), instanceOf(org.apache.tools.ant.filters.HeadFilter))
        assertThat(spec.filterChain.getLastFilter().in, instanceOf(org.apache.tools.ant.filters.StripJavaComments))
    }

    @Test public void testAddsNameTransformerToDestinationMapper() {
        spec.rename("regexp", "replacement")

        assertThat(spec.destinationMapper.nameTransformer.transformers.size(), equalTo(1))
        assertThat(spec.destinationMapper.nameTransformer.transformers[0], instanceOf(RegExpNameMapper))
    }

    @Test public void testAddsClosureToDestinationMapper() {
        spec.rename {}

        assertThat(spec.destinationMapper.nameTransformer.transformers.size(), equalTo(1))
    }

    @Test public void testDefaultPermissions() {
        assertEquals(UnixStat.DEFAULT_FILE_PERM, spec.fileMode)
        assertEquals(UnixStat.DEFAULT_DIR_PERM, spec.dirMode)
    }

    @Test public void testInheritsPermissionsFromParent() {
        spec.fileMode = 0x1
        spec.dirMode = 0x2

        CopySpecImpl child = spec.from('src') { }
        assertEquals(0x1, child.fileMode)
        assertEquals(0x2, child.dirMode)
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

}

