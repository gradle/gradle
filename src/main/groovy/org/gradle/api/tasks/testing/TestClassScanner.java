package org.gradle.api.tasks.testing;

import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;
import org.gradle.api.testing.TestFramework;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;
import java.io.File;

import groovy.util.AntBuilder;

/**
 * @author Tom Eyckmans
 */
public class TestClassScanner {
    private static final Logger logger = LoggerFactory.getLogger(TestClassScanner.class);
    private final File testClassDirectory;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final TestFramework testFramework;
    private final AntBuilder antBuilder;
    private final boolean scanForTestClasses;

    public TestClassScanner(File testClassDirectory, List<String> includePatterns, List<String> excludePatterns, TestFramework testFramework, AntBuilder antBuilder, boolean scanForTestClasses) {
        this.testClassDirectory = testClassDirectory;
        this.includePatterns = includePatterns == null ? new ArrayList<String>() : new ArrayList<String>(includePatterns);
        this.excludePatterns = excludePatterns == null ? new ArrayList<String>() : new ArrayList<String>(excludePatterns);
        this.testFramework = testFramework;
        this.antBuilder = antBuilder;
        this.scanForTestClasses = scanForTestClasses;
    }

    public Set<String> getTestClassNames() {
        final FileSet testClassFileSet = new FileSet();

        testClassFileSet.setProject(antBuilder.getAntProject());
        testClassFileSet.setDir(testClassDirectory);

        if ( !scanForTestClasses ) {
            if ( includePatterns.isEmpty() ) {
                includePatterns.add("**/*Tests.class");
                includePatterns.add("**/*Test.class");
            }
            if ( excludePatterns.isEmpty() ) {
                excludePatterns.add("**/Abstract*.class");
            }
        }
        else {
            if ( includePatterns.isEmpty() ) {
                includePatterns.add("**/*.class");
            }
        }

        if ( includePatterns != null && !includePatterns.isEmpty() )
            testClassFileSet.appendIncludes(includePatterns.toArray(new String[includePatterns.size()]));

        if ( excludePatterns != null && !excludePatterns.isEmpty() )
            testClassFileSet.appendExcludes(excludePatterns.toArray(new String[excludePatterns.size()]));

        final Iterator testClassFilesIterator = testClassFileSet.iterator();
        final Set<String> testClassNames = new HashSet<String>();
        while ( testClassFilesIterator.hasNext() ) {
            final FileResource fileResource = (FileResource)testClassFilesIterator.next();

            if ( !fileResource.isDirectory() ) {
                logger.debug("test-class-scan : scanning " + fileResource.getName() );

                if ( scanForTestClasses ) {
                    if (!testFramework.isTestClass(fileResource.getFile()) ) {
                        logger.debug("test-class-scan : discarded " + fileResource.getName() + " not a test class");
                    }
                }
                else
                    testClassNames.add(fileResource.getName());
            }
        }

        if ( scanForTestClasses )
            return testFramework.getTestClassNames();
        else
            return testClassNames;
    }
}
