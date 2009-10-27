package org.gradle.api.internal.file

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.util.TemporaryFolder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.integtests.TestFile

public class CopySpecImplTest {

    private CopySpecImpl spec;
    @Rule public TemporaryFolder testDir = new TemporaryFolder();
    private TestFile baseFile = testDir.dir
    private final FileResolver fileResolver = new BaseDirConverter(baseFile);

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
        assertEquals([sources], spec.getAllSourcePaths() as List);
    }

    @Test public void testFromArray() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources as File[]);
        assertEquals(sources, spec.getAllSourcePaths() as List);
    }

    @Test public void testHierarchical() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources);

        CopySpecImpl childSpec = new CopySpecImpl(fileResolver, spec);
        childSpec.from('childFile');

        assertEquals([sources, 'childFile'], childSpec.getAllSourcePaths() as List);
    }

    @Test public void testSourceWithClosure() {
        File sourceFile = getAbsoluteTestSources().get(0);

        spec.from(sourceFile) {
            into 'target'
        }

        List specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        assertEquals([sourceFile], theSpec.getAllSourcePaths() as List);

        assertThat(theSpec.destPath, equalTo('/target'))
    }

    @Test public void testMultipleSourcesWithClosure() {
        List sources = getAbsoluteTestSources()

        spec.from(sources) {
            into 'target'
        }

        List specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        assertEquals([sources], theSpec.getAllSourcePaths() as List)

        assertThat(theSpec.destPath, equalTo('/target'))
    }

    @Test public void testDestinationWithClosure() {
        File sourceFile = getAbsoluteTestSources().get(0);

        spec.into('target') {
            from sourceFile
        }

        List specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        assertEquals([sourceFile], theSpec.getAllSourcePaths() as List);

        assertThat(theSpec.destPath, equalTo('/target'))
    }

    @Test public void testRootSpecResolvesIntoArgAsDestinationDir() {
        spec.into 'somedir'
        assertThat(spec.destPath, equalTo('/'))
        assertThat(spec.destDir, equalTo(baseFile.file('somedir')))
    }

    @Test public void testRootSpecHasNoDefaultDestinationDir() {
        assertThat(spec.destPath, equalTo('/'))
        assertThat(spec.destDir, nullValue())
    }

    @Test public void testChildSpecResolvesIntoArgRelativeToParentDestinationDir() {
        spec.into 'dest'
        CopySpecImpl child = spec.from('somedir') { into 'child' }
        assertThat(child.destPath, equalTo('/child'))
        assertThat(child.destDir, equalTo(baseFile.file('dest/child')))

        CopySpecImpl grandchild = child.from('somedir') { into 'grandchild'}
        assertThat(grandchild.destPath, equalTo('/child/grandchild'))
        assertThat(grandchild.destDir, equalTo(baseFile.file('dest/child/grandchild')))

        grandchild.into '/grandchild'
        assertThat(grandchild.destPath, equalTo('/grandchild'))
        assertThat(grandchild.destDir, equalTo(baseFile.file('dest/grandchild')))
    }

    @Test public void testChildSpecUsesParentDestinationDirAsDefault() {
        spec.into 'dest'
        CopySpecImpl child = spec.from('somedir') { }
        assertThat(child.destPath, equalTo('/'))
        assertThat(child.destDir, equalTo(baseFile.file('dest')))

        child.into 'child'

        CopySpecImpl grandchild = child.from('somedir') { }
        assertThat(grandchild.destPath, equalTo('/child'))
        assertThat(grandchild.destDir, equalTo(baseFile.file('dest/child')))
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
}
