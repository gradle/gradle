package org.gradle.api.internal.file;

import org.apache.tools.ant.filters.HeadFilter
import org.apache.tools.ant.filters.StripJavaComments
import org.gradle.util.HelperUtil
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*

public class CopySpecImplTest {

    private CopySpecImpl spec;
    private File baseFile = HelperUtil.makeNewTestDir();
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
        File destFile = new File(baseFile, 'target');

        spec.from(sourceFile) {
            into destFile
        }

        List specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        assertEquals([sourceFile], theSpec.getAllSourcePaths() as List);

        assertEquals(destFile, theSpec.getDestDir())
    }

    @Test public void testIterableWithClosure() {
        List sources = getAbsoluteTestSources()
        File destFile = new File(baseFile, 'target');

        spec.from(sources) {
            into destFile
        }

        List specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        assertEquals([sources], theSpec.getAllSourcePaths() as List)

        assertEquals(destFile, theSpec.getDestDir())
    }

    @Test public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.filterChain.getLastFilter(), Matchers.instanceOf(StripJavaComments))
    }

    @Test public void testArgFilter() {
        spec.filter(HeadFilter, lines:15, skip:2)

        org.apache.tools.ant.filters.HeadFilter filter = spec.filterChain.getLastFilter()
        assertThat(filter, Matchers.instanceOf(HeadFilter))
        assertEquals(15, filter.lines)
        assertEquals(2, filter.skip)
    }

    @Test public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines:15, skip:2)
        
        assertThat(spec.filterChain.getLastFilter(), Matchers.instanceOf(org.apache.tools.ant.filters.HeadFilter))
        assertThat(spec.filterChain.getLastFilter().in, Matchers.instanceOf(org.apache.tools.ant.filters.StripJavaComments))
    }


}
