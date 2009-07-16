package org.gradle.api.tasks.copy;

import org.junit.Test;
import org.junit.Before;
import groovy.util.AntBuilder;
import static org.junit.Assert.*;
import org.gradle.api.internal.tasks.copy.CopySpecImpl;
import org.apache.tools.ant.filters.*
import org.hamcrest.Matchers

import java.io.File;
import java.util.List;
import java.util.ArrayList
import org.gradle.api.internal.tasks.copy.CopySpecImpl
import org.gradle.api.Project;
import org.gradle.util.HelperUtil;


public class CopySpecImplTest {

    private AntBuilder ant;
    private CopySpecImpl spec;
    private File baseFile;
    private final Project project = HelperUtil.createRootProject();

    @Before
    public void setUp() {
        baseFile = project.getBuildDir()
        if (ant == null) {
            ant = new AntBuilder();
        }
        spec = new CopySpecImpl(project);
    }


    private List<String> getTestSourceFileNames() {
        List<String> names = new ArrayList<String>();
        names.add("first");
        names.add("second");
        return names;
    }

    private List<File> getAbsoluteTestSources() {
        List<File> sources = new ArrayList<File>();
        List<String> names = getTestSourceFileNames();
        for (String name : names) {
            sources.add(new File(baseFile, name));
        }
        return sources;
    }

    private List<File> getRelativeTestSources() {
        List<File> sources = new ArrayList<File>();
        List<String> names = getTestSourceFileNames();
        for (String name : names) {
            sources.add(new File(name));
        }
        return sources;
    }

    @Test public void testAbsoluteFromList() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources);
        assertEquals(sources, spec.getAllSourceDirs());
    }

    @Test public void testRelativeFromList() {
        List<File> sources = getRelativeTestSources();
        spec.from(sources);

        List<File> resolvedSources = sources.collect { new File(project.getProjectDir(), it.path)}
        assertEquals(resolvedSources, spec.getAllSourceDirs());
    }

    @Test public void testFromArray() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources as File[]);
        assertEquals(sources, spec.getAllSourceDirs());
    }

    @Test public void testHierarchical() {
        List<File> sources = getAbsoluteTestSources();
        spec.from(sources);

        File childFile = new File(project.getProjectDir(), "childFile")
        CopySpecImpl childSpec = new CopySpecImpl(project, spec);
        childSpec.from(childFile.path);

        sources.add(childFile);
        assertEquals(sources, childSpec.getAllSourceDirs());
    }

    @Test public void testSourceWithClosure() {
        File sourceFile = getAbsoluteTestSources().get(0);
        File destFile = new File(project.projectDir, 'target');

        spec.from(sourceFile) {
            into destFile
        }

        ArrayList specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        ArrayList resultingSources = theSpec.getAllSourceDirs()
        assertEquals(1, resultingSources.size())
        assertEquals(sourceFile, resultingSources.get(0))

        assertEquals(destFile, theSpec.getDestDir()) 
    }

    @Test public void testIterableWithClosure() {
        HashSet sources = new HashSet(getAbsoluteTestSources())
        File destFile = new File(project.projectDir, 'target');

        spec.from(sources) {
            into destFile
        }

        ArrayList specs = spec.getLeafSyncSpecs()
        assertEquals(1, specs.size())
        CopySpecImpl theSpec = specs.get(0)
        ArrayList resultingSources = theSpec.getAllSourceDirs()
        assertEquals(sources, new HashSet(resultingSources))

        assertEquals(destFile, theSpec.getDestDir())
    }

    @Test public void testNoArgFilter() {
        spec.filter(StripJavaComments)
        assertThat(spec.filterChain.chain, Matchers.instanceOf(StripJavaComments))
    }

    @Test public void testArgFilter() {
        spec.filter(HeadFilter, lines:15, skip:2)

        org.apache.tools.ant.filters.HeadFilter filter = spec.filterChain.chain
        assertThat(filter, Matchers.instanceOf(HeadFilter))
        assertEquals(15, filter.lines)
        assertEquals(2, filter.skip)
    }

    @Test public void testTwoFilters() {
        spec.filter(StripJavaComments)
        spec.filter(HeadFilter, lines:15, skip:2)
        
        assertThat(spec.filterChain.chain, Matchers.instanceOf(org.apache.tools.ant.filters.HeadFilter))
        assertThat(spec.filterChain.chain.in, Matchers.instanceOf(org.apache.tools.ant.filters.StripJavaComments))
    }


}
