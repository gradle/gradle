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

package org.gradle.api.tasks.testing;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.*;
import org.gradle.api.testing.TestFramework;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.compile.ClasspathConverter;
import org.gradle.external.junit.JUnitTestFramework;
import org.gradle.external.testng.TestNGTestFramework;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.File;
import java.util.*;

/**
 * A task for executing Junit 3.8.x and Junit 4 tests.
 *
 * @author Hans Dockter
 */
public class Test extends ConventionTask {
    private static final Logger logger = LoggerFactory.getLogger(Test.class);
    public static final String FAILURES_OR_ERRORS_PROPERTY = "org.gradle.api.tasks.testing.failuresOrErrors";

    public static final String TEST_FRAMEWORK_DEFAULT_PROPERTY = "test.framework.default";

    private List testSrcDirs = null;

    private File testClassesDir = null;

    private File testResultsDir = null;

    private File testReportDir = null;

    private List<String> includes;

    private List<String> excludes;

    private boolean stopAtFailuresOrErrors = true;

    private List unmanagedClasspath = null;

    private FileCollection configuration;

    protected ExistingDirsFilter existingDirsFilter = new ExistingDirsFilter();

    protected ClasspathConverter classpathConverter = new ClasspathConverter();

    private TestFramework testFramework = null;

    private boolean testReport = true;

    private boolean scanForTestClasses = true;

    public Test(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                executeTests();
            }
        });
    }

    protected void executeTests() {
        final File testClassesDir = getTestClassesDir();

        if (testClassesDir == null)
            throw new InvalidUserDataException("The testClassesDir property is not set, testing can't be triggered!");
        if (getTestResultsDir() == null)
            throw new InvalidUserDataException("The testResultsDir property is not set, testing can't be triggered!");

        existingDirsFilter.checkExistenceAndThrowStopActionIfNot(testClassesDir);

        final TestFramework testFramework = getTestFramework();

        testFramework.prepare(getProject(), this);

        final List<String> includes = getIncludes();
        final List<String> excludes = getExcludes();

        final TestClassScanner testClassScanner = new TestClassScanner(
                testClassesDir,
                includes, excludes,
                testFramework,
                getProject().getAnt() ,
                scanForTestClasses
        );

        final Set<String> testClassNames = testClassScanner.getTestClassNames();

        Collection<String> toUseIncludes = null;
        Collection<String> toUseExcludes = null;
        if ( testClassNames.isEmpty() ) {
            toUseIncludes = includes;
            toUseExcludes = excludes;
        }
        else {
            toUseIncludes = testClassNames;
            toUseExcludes = new ArrayList<String>();
        }
        
        testFramework.execute(getProject(), this, toUseIncludes, toUseExcludes);

        if (testReport) {
            testFramework.report(getProject(), this);
        }

        if (stopAtFailuresOrErrors && GUtil.isTrue(getProject().getAnt().getProject().getProperty(FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }

    public List getClasspath() {
        return classpathConverter.createFileClasspath(getProject().getRootDir(),
                GUtil.addLists(WrapUtil.toList(getTestClassesDir()), getUnmanagedClasspath(), configuration));
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setIncludes(java.util.List)
     */
    public Test include(String... includes) {
        this.includes = GUtil.chooseCollection(this.includes, getExcludes());
        this.includes.addAll(Arrays.asList(includes));
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     * @see #setExcludes(java.util.List) (java.util.List)
     */
    public Test exclude(String... excludes) {
        this.excludes = GUtil.chooseCollection(this.excludes, getExcludes());
        this.excludes.addAll(Arrays.asList(excludes));
        return this;
    }

    /**
     * This methode is usually used only internally by Gradle.
     * A list of files are added to the compile classpath. The files should point to jars or directories containing
     * class files. The files added here are not shared in a multi-project build and are not listed in
     * a dependency descriptor if you upload your library to a repository.
     * @param elements The elements to be added
     * @return this
     */
    public Test unmanagedClasspath(Object... elements) {
        this.unmanagedClasspath = GUtil.chooseCollection(this.unmanagedClasspath, getUnmanagedClasspath());
        unmanagedClasspath.addAll(GUtil.flatten(Arrays.asList(elements)));
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources.
     */
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
    public List<String> getIncludes() {
        return includes;
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String[])
     */
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #include(String[])
     */
    public List<String> getExcludes() {
        return excludes;
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String[])
     */
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * Returns whether this task should throw an exception in case of test failuer or error.
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

    /**
     * Returns the unmanaged classpath.
     *
     * @see #unmanagedClasspath(Object[])
     */
    public List getUnmanagedClasspath() {
        return unmanagedClasspath;
    }

    /**
     * Sets the unmanaged classpath.
     *
     * @see #unmanagedClasspath(Object[])
     */
    public void setUnmanagedClasspath(List unmanagedClasspath) {
        this.unmanagedClasspath = unmanagedClasspath;
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

    public FileCollection getConfiguration() {
        return configuration;
    }

    public void setConfiguration(FileCollection configuration) {
        this.configuration = configuration;
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

    public List getTestSrcDirs() {
        return testSrcDirs;
    }

    public void setTestSrcDirs(List testSrcDir) {
        this.testSrcDirs = testSrcDir;
    }

    public boolean isScanForTestClasses() {
        return scanForTestClasses;
    }

    public void setScanForTestClasses(boolean scanForTestClasses) {
        this.scanForTestClasses = scanForTestClasses;
    }
}
