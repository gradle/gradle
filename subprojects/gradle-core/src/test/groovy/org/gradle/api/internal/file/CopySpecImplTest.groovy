package org.gradle.api.internal.file

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.api.file.RelativePath
import org.gradle.integtests.TestFile
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.apache.tools.zip.UnixStat

@RunWith(JMock)
public class CopySpecImplTest {

    private CopySpecImpl spec;
    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    private TestFile baseFile = testDir.dir
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final FileResolver fileResolver = context.mock(FileResolver);

    @Before
    public void setUp() {
        spec = new CopySpecImpl(fileResolver);
    }

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

        assertEquals(['source'], child.sourcePaths as List);
    }

    @Test public void testMultipleSourcesWithClosure() {
        CopySpecImpl child = spec.from(['source1', 'source2']) {
        }

        assertEquals(['source1', 'source2'], child.sourcePaths.flatten() as List);
    }

    @Test public void testDestinationWithClosure() {
        CopySpecImpl child = spec.into('target') {
        }

        assertThat(child.destPath, equalTo(new RelativePath(false, 'target')))
    }

    @Test public void testGetAllSpecsReturnsBreadthwiseTraverseOfSpecs() {
        CopySpecImpl child = spec.into('somedir') { }
        CopySpecImpl grandchild = child.into('somedir') { }
        CopySpecImpl child2 = spec.into('somedir') { }

        assertThat(spec.allSpecs, equalTo([spec, child, grandchild, child2]))
    }
    
    @Test public void testRootSpecResolvesItsIntoArgAsDestinationDir() {
        spec.into 'somedir'
        assertThat(spec.destPath, equalTo(new RelativePath(false)))

        context.checking {
            allowing(fileResolver).resolve('somedir')
            will(returnValue(baseFile))
        }

        assertThat(spec.destDir, equalTo(baseFile))
    }

    @Test public void testRootSpecHasNoDefaultDestinationDir() {
        assertThat(spec.destPath, equalTo(new RelativePath(false)))
        assertThat(spec.destDir, nullValue())
    }

    @Test public void testChildSpecResolvesIntoArgRelativeToParentDestinationDir() {
        spec.into 'dest'

        context.checking {
            allowing(fileResolver).resolve('dest')
            will(returnValue(baseFile))
        }

        CopySpecImpl child = spec.from('somedir') { into 'child' }
        assertThat(child.destPath, equalTo(new RelativePath(false, 'child')))

        CopySpecImpl grandchild = child.from('somedir') { into 'grandchild'}
        assertThat(grandchild.destPath, equalTo(new RelativePath(false, 'child', 'grandchild')))

        grandchild.into '/grandchild'
        assertThat(grandchild.destPath, equalTo(new RelativePath(false, 'grandchild')))
    }

    @Test public void testChildSpecUsesParentDestinationDirAsDefault() {
        spec.into 'dest'

        context.checking {
            allowing(fileResolver).resolve('dest')
            will(returnValue(baseFile))
        }

        CopySpecImpl child = spec.from('somedir') { }
        assertThat(child.destPath, equalTo(new RelativePath(false)))

        child.into 'child'

        CopySpecImpl grandchild = child.from('somedir') { }
        assertThat(grandchild.destPath, equalTo(new RelativePath(false, 'child')))
    }

    @Test public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.filterChain.getLastFilter(), instanceOf(StripJavaComments))
    }

    @Test public void testArgFilter() {
        spec.filter(HeadFilter, lines:15, skip:2)

        org.apache.tools.ant.filters.HeadFilter filter = spec.filterChain.getLastFilter()
        assertThat(filter, instanceOf(HeadFilter))
        assertEquals(15, filter.lines)
        assertEquals(2, filter.skip)
    }

    @Test public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines:15, skip:2)
        
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

    @Test public void testDefaultValues() {
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
}
