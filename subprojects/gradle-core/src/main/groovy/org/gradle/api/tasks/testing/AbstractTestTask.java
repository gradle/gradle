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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.testing.fabric.TestFramework;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.external.junit.JUnitTestFramework;
import org.gradle.external.testng.TestNGTestFramework;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A base task for executing tests.
 *
 * @author Hans Dockter
 */
public abstract class AbstractTestTask extends ConventionTask implements PatternFilterable {
    public static final String FAILURES_OR_ERRORS_PROPERTY = "org.gradle.api.tasks.testing.failuresOrErrors";

    public static final String TEST_FRAMEWORK_DEFAULT_PROPERTY = "test.framework.default";

    protected List<File> testSrcDirs = new ArrayList<File>();

    protected File testClassesDir;

    protected File testResultsDir;

    protected File testReportDir;

    protected PatternFilterable patternSet = new PatternSet();

    protected boolean stopAtFailuresOrErrors = true;

    protected FileCollection classpath;

    private TestFrameworkInstance testFrameworkInstance;

    protected boolean testReport = true;

    protected boolean scanForTestClasses = true;

    @TaskAction
    protected abstract void executeTests();

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    public AbstractTestTask include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    public AbstractTestTask include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    public AbstractTestTask include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    public AbstractTestTask include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    public AbstractTestTask exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    public AbstractTestTask exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    public AbstractTestTask exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    public AbstractTestTask exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources.
     *
     * @return All test class directories to be used.
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
     *
     * @return the test result directory, containing the internal test results, mostly in xml form.
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
     *
     * @return the test report directory, containing the test report mostly in HTML form.
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
     * @see #include(String[])
     */
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String[])
     */
    public AbstractTestTask setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #include(String[])
     */
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String[])
     */
    public AbstractTestTask setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    /**
     * @return whether this task should throw an exception in case of test failure or error.
     */
    public boolean isStopAtFailuresOrErrors() {
        return stopAtFailuresOrErrors;
    }

    /**
     * Sets whether this task should throw an exception in case of test failuer or error.
     *
     * @param stopAtFailuresOrErrors The new stop at failures or errors value.
     */
    public void setStopAtFailuresOrErrors(boolean stopAtFailuresOrErrors) {
        this.stopAtFailuresOrErrors = stopAtFailuresOrErrors;
    }

    public TestFrameworkInstance getTestFramework() {
        return testFramework(null);
    }

    public TestFrameworkInstance testFramework(Closure testFrameworkConfigure) {
        if (testFrameworkInstance == null) {
            return useDefaultTestFramework(testFrameworkConfigure);
        }

        return testFrameworkInstance;
    }


    /**
     * Backwards compatible access to the TestFramework options.
     * <p/>
     * Be sure to call the appropriate useJUnit/useTestNG/useTestFramework function or set the default before using this function.
     *
     * @return The testframework options.
     */
    public Object getOptions() {
        return options(null);
    }

    public Object options(Closure testFrameworkConfigure) {
        final Object options = getTestFramework().getOptions();

        if (testFrameworkConfigure != null)
            ConfigureUtil.configure(testFrameworkConfigure, testFrameworkInstance.getOptions());

        return options;
    }

    public TestFrameworkInstance useTestFramework(TestFramework testFramework) {
        return useTestFramework(testFramework, null);
    }

    public TestFrameworkInstance useTestFramework(TestFramework testFramework, Closure testFrameworkConfigure) {
        if (testFramework == null)
            throw new IllegalArgumentException("testFramework is null!");

        this.testFrameworkInstance = testFramework.getInstance(this);

        testFrameworkInstance.initialize(getProject(), this);

        if (testFrameworkConfigure != null)
            ConfigureUtil.configure(testFrameworkConfigure, testFrameworkInstance.getOptions());

        return testFrameworkInstance;
    }

    public TestFrameworkInstance useJUnit() {
        return useJUnit(null);
    }

    public TestFrameworkInstance useJUnit(Closure testFrameworkConfigure) {
        return useTestFramework(new JUnitTestFramework(), testFrameworkConfigure);
    }

    public TestFrameworkInstance useTestNG() {
        return useTestNG(null);
    }

    public TestFrameworkInstance useTestNG(Closure testFrameworkConfigure) {
        return useTestFramework(new TestNGTestFramework(), testFrameworkConfigure);
    }

    public TestFrameworkInstance useDefaultTestFramework(Closure testFrameworkConfigure) {
        try {
            final String testFrameworkDefault = (String) getProject().property(TEST_FRAMEWORK_DEFAULT_PROPERTY);

            if (testFrameworkDefault == null || "".equals(testFrameworkDefault) || "junit".equalsIgnoreCase(testFrameworkDefault)) {
                return useJUnit(testFrameworkConfigure);
            } else if ("testng".equalsIgnoreCase(testFrameworkDefault)) {
                return useTestNG(testFrameworkConfigure);
            } else {
                try {
                    final Class testFrameworkClass = Class.forName(testFrameworkDefault);

                    return useTestFramework((TestFramework) testFrameworkClass.newInstance(), testFrameworkConfigure);
                } catch (ClassNotFoundException e) {
                    throw new GradleException(testFrameworkDefault + " could not be found on the classpath", e);
                } catch (Exception e) {
                    throw new GradleException("Could not create an instance of the test framework class " + testFrameworkDefault + ". Make sure that it has a public noargs constructor.", e);
                }

            }
        }
        catch (MissingPropertyException e) {
            return useJUnit(testFrameworkConfigure);
        }
    }

    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
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
