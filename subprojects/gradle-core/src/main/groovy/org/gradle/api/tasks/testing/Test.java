/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.tasks.testing;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.GradleException;
import org.gradle.api.specs.Spec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.testing.TestFramework;
import org.gradle.external.junit.JUnitTestFramework;
import org.gradle.external.testng.TestNGTestFramework;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A task for executing JUnit (3.8.x or 4) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class Test extends ConventionTask implements PatternFilterable {
    private static final Logger logger = LoggerFactory.getLogger(Test.class);

    public static final String FAILURES_OR_ERRORS_PROPERTY = "org.gradle.api.tasks.testing.failuresOrErrors";

    public static final String TEST_FRAMEWORK_DEFAULT_PROPERTY = "test.framework.default";

    private List<File> testSrcDirs = null;

    private File testClassesDir = null;

    private File testResultsDir = null;

    private File testReportDir = null;

    private PatternFilterable patternSet = new PatternSet();
    
    private boolean stopAtFailuresOrErrors = true;

    private FileCollection classpath;

    private TestFramework testFramework = null;

    private boolean testReport = true;

    private boolean scanForTestClasses = true;

    @TaskAction
    protected void executeTests() {
        final File testClassesDir = getTestClassesDir();

        final TestFramework testFramework = getTestFramework();

        testFramework.prepare(getProject(), this);

        final Set<String> includes = getIncludes();
        final Set<String> excludes = getExcludes();

        final TestClassScanner testClassScanner = new TestClassScanner(
                testClassesDir,
                includes, excludes,
                testFramework,
                scanForTestClasses
        );

        final Set<String> testClassNames = testClassScanner.getTestClassNames();

        Collection<String> toUseIncludes;
        Collection<String> toUseExcludes;
        if ( testClassNames.isEmpty() ) {
            toUseIncludes = includes;
            toUseExcludes = excludes;
        }
        else {
            toUseIncludes = testClassNames;
            toUseExcludes = new ArrayList<String>();
        }

        GFileUtils.createDirectoriesWhenNotExistent(getTestResultsDir());// needed for JUnit reporting

        if ( !(toUseIncludes.isEmpty() && toUseExcludes.isEmpty()))
            testFramework.execute(getProject(), this, toUseIncludes, toUseExcludes);
        else // when there are no includes/excludes -> don't execute test framework
            logger.debug("skipping test execution, because no tests were found");
        // TestNG execution fails when there are no tests
        // JUnit execution doesn't fail when there are no tests

        if (testReport) {
            testFramework.report(getProject(), this);
        }

        if (stopAtFailuresOrErrors && GUtil.isTrue(getProject().getAnt().getProject().getProperty(FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setIncludes(Iterable)
     */
    public Test include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setIncludes(Iterable)
     */
    public Test include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public Test include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public Test include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setExcludes(Iterable)
     */
    public Test exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setExcludes(Iterable) 
     */
    public Test exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public Test exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public Test exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources.
     */
    @InputDirectory @SkipWhenEmpty
    public File getTestClassesDir() {
        return testClassesDir;
    }

    /**
     * Sets the root folder for the compiled test sources.
     *
     * @param testClassesDir The root folder
     */
    public void setTestClassesDir(File testClassesDir) {
        this.testClassesDir = testClassesDir;
    }

    /**
     * Returns the root folder for the test results.
     */
    @OutputDirectory
    public File getTestResultsDir() {
        return testResultsDir;
    }

    /**
     * Sets the root folder for the test results.
     *
     * @param testResultsDir The root folder
     */
    public void setTestResultsDir(File testResultsDir) {
        this.testResultsDir = testResultsDir;
    }

    /**
     * Returns the root folder for the test reports.
     */
    @OutputDirectory
    public File getTestReportDir() {
        return testReportDir;
    }

    /**
     * Sets the root folder for the test reports.
     *
     * @param testReportDir The root folder
     */
    public void setTestReportDir(File testReportDir) {
        this.testReportDir = testReportDir;
    }

    /**
     * Returns the include patterns for test execution.
     *
     * @see #include(String...)
     */
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String...)
     */
    public Test setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #exclude(String...)
     */
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String...) 
     */
    public Test setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    /**
     * Returns whether this task should throw an exception in case of test failure or error.
     */
    public boolean isStopAtFailuresOrErrors() {
        return stopAtFailuresOrErrors;
    }

    /**
     * Sets whether this task should throw an exception in case of test failuer or error.
     */
    public void setStopAtFailuresOrErrors(boolean stopAtFailuresOrErrors) {
        this.stopAtFailuresOrErrors = stopAtFailuresOrErrors;
    }

    public TestFramework getTestFramework() {
        return testFramework(null);
    }

    public TestFramework testFramework(Closure testFrameworkConfigure) {
        if ( testFramework == null ) {
            return useDefaultTestFramework(testFrameworkConfigure);
        }

        return testFramework;
    }

    /**
     * Backwards compatible access to the TestFramework options.
     *
     * Be sure to call the appropriate useJUnit/useTestNG/useTestFramework function or set the default before using this function.
     *
     * @return The testframework options.
     */
    public Object getOptions() {
        return options(null);
    }

    public Object options(Closure testFrameworkConfigure)
    {
        final Object options = getTestFramework().getOptions();

        if ( testFrameworkConfigure != null )
            ConfigureUtil.configure(testFrameworkConfigure, testFramework.getOptions());

        return options;
    }

    public TestFramework useTestFramework(TestFramework testFramework) {
        return useTestFramework(testFramework, null);
    }

    public TestFramework useTestFramework(TestFramework testFramework, Closure testFrameworkConfigure) {
        if ( testFramework == null )
            throw new IllegalArgumentException("testFramework is null!");

        testFramework.initialize(getProject(), this);

        this.testFramework = testFramework;

        if ( testFrameworkConfigure != null )
            ConfigureUtil.configure(testFrameworkConfigure, testFramework.getOptions());

        return testFramework;
    }

    public TestFramework useJUnit() {
        return useJUnit(null);
    }

    public TestFramework useJUnit(Closure testFrameworkConfigure) {
        return useTestFramework(new JUnitTestFramework(), testFrameworkConfigure);
    }

    public TestFramework useTestNG() {
        return useTestNG(null);
    }

    public TestFramework useTestNG(Closure testFrameworkConfigure) {
        return useTestFramework(new TestNGTestFramework(), testFrameworkConfigure);
    }

    public TestFramework useDefaultTestFramework(Closure testFrameworkConfigure) {
        try {
            final String testFrameworkDefault = (String)getProject().property(TEST_FRAMEWORK_DEFAULT_PROPERTY);

            if ( testFrameworkDefault == null || "".equals(testFrameworkDefault) || "junit".equalsIgnoreCase(testFrameworkDefault) ) {
                return useJUnit(testFrameworkConfigure);
            }
            else if ( "testng".equalsIgnoreCase(testFrameworkDefault) ) {
                return useTestNG(testFrameworkConfigure);
            }
            else {
                try {
                    final Class testFrameworkClass = Class.forName(testFrameworkDefault);

                    return useTestFramework((TestFramework)testFrameworkClass.newInstance(), testFrameworkConfigure);
                } catch (ClassNotFoundException e) {
                    throw new GradleException(testFrameworkDefault + " could not be found on the classpath", e);
                } catch (Exception e) {
                    throw new GradleException("Could not create an instance of the test framework class " + testFrameworkDefault + ". Make sure that it has a public noargs constructor.", e);
                }

            }
        }
        catch ( MissingPropertyException e ) {
            return useJUnit(testFrameworkConfigure);
        }
    }

    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection configuration) {
        this.classpath = configuration;
    }

    public boolean isTestReport() {
        return testReport;
    }

    public void setTestReport(boolean testReport) {
        this.testReport = testReport;
    }

    public void enableTestReport() {
        this.testReport = true;
    }

    public void disableTestReport() {
        this.testReport = false;
    }

    @InputFiles
    public List<File> getTestSrcDirs() {
        return testSrcDirs;
    }

    public void setTestSrcDirs(List<File> testSrcDir) {
        this.testSrcDirs = testSrcDir;
    }

    public boolean isScanForTestClasses() {
        return scanForTestClasses;
    }

    public void setScanForTestClasses(boolean scanForTestClasses) {
        this.scanForTestClasses = scanForTestClasses;
    }
}
