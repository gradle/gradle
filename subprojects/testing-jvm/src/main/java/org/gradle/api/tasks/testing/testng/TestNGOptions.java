/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing.testng;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.xml.MarkupBuilder;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestClassProcessor;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The TestNG specific test options.
 */
public abstract class TestNGOptions extends TestFrameworkOptions {
    public static final String DEFAULT_CONFIG_FAILURE_POLICY = TestNGTestClassProcessor.DEFAULT_CONFIG_FAILURE_POLICY;
    private static final String DEFAULT_PARALLEL_MODE = null;
    private static final int DEFAULT_THREAD_COUNT = -1;

    private File outputDirectory;

    private Set<String> includeGroups = new LinkedHashSet<String>();

    private Set<String> excludeGroups = new LinkedHashSet<String>();

    private String configFailurePolicy = DEFAULT_CONFIG_FAILURE_POLICY;

    private Set<String> listeners = new LinkedHashSet<String>();

    private String parallel = DEFAULT_PARALLEL_MODE;

    private int threadCount = DEFAULT_THREAD_COUNT;

    private boolean useDefaultListeners;

    private String suiteName = "Gradle suite";

    private String testName = "Gradle test";

    private List<File> suiteXmlFiles = new ArrayList<File>();

    private boolean preserveOrder;

    private boolean groupByInstances;

    private transient StringWriter suiteXmlWriter;

    private transient MarkupBuilder suiteXmlBuilder;

    private final File projectDir;

    @Inject
    public TestNGOptions(ProjectLayout projectLayout) {
        this.projectDir = projectLayout.getProjectDirectory().getAsFile();
    }

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(TestNGOptions other) {
        this.outputDirectory = other.outputDirectory;
        replace(this.includeGroups, other.includeGroups);
        replace(this.excludeGroups, other.excludeGroups);
        this.configFailurePolicy = other.configFailurePolicy;
        replace(this.listeners, other.listeners);
        this.parallel = other.parallel;
        this.threadCount = other.threadCount;
        this.useDefaultListeners = other.useDefaultListeners;
        this.suiteName = other.suiteName;
        this.testName = other.testName;
        replace(this.suiteXmlFiles, other.suiteXmlFiles);
        this.preserveOrder = other.preserveOrder;
        this.groupByInstances = other.groupByInstances;
        // not copying suiteXmlWriter as it is transient
        // not copying suiteXmlBuilder as it is transient
    }

    private static <T> void replace(Collection<T> target, Collection<T> source) {
        target.clear();
        target.addAll(source);
    }

    public MarkupBuilder suiteXmlBuilder() {
        suiteXmlWriter = new StringWriter();
        suiteXmlBuilder = new MarkupBuilder(suiteXmlWriter);
        return suiteXmlBuilder;
    }

    /**
     * Add suite files by Strings. Each suiteFile String should be a path relative to the project root.
     */
    public void suites(String... suiteFiles) {
        for (String suiteFile : suiteFiles) {
            suiteXmlFiles.add(new File(TestNGOptions.this.getProjectDir(), suiteFile));
        }
    }

    @Internal
    protected File getProjectDir() {
        return projectDir;
    }

    /**
     * Add suite files by File objects.
     */
    public void suites(File... suiteFiles) {
        suiteXmlFiles.addAll(Arrays.asList(suiteFiles));
    }

    public List<File> getSuites(File testSuitesDir) {
        List<File> suites = new ArrayList<File>();

        suites.addAll(suiteXmlFiles);

        if (suiteXmlBuilder != null) {
            File buildSuiteXml = new File(testSuitesDir.getAbsolutePath(), "build-suite.xml");

            if (buildSuiteXml.exists()) {
                if (!buildSuiteXml.delete()) {
                    throw new RuntimeException("failed to remove already existing build-suite.xml file");
                }
            }

            IoActions.writeTextFile(buildSuiteXml, new ErroringAction<BufferedWriter>() {
                @Override
                protected void doExecute(BufferedWriter writer) throws Exception {
                    writer.write("<!DOCTYPE suite SYSTEM \"http://testng.org/testng-1.0.dtd\">");
                    writer.newLine();
                    writer.write(getSuiteXml());
                }
            });

            suites.add(buildSuiteXml);
        }

        return suites;
    }

    public TestNGOptions includeGroups(String... includeGroups) {
        this.includeGroups.addAll(Arrays.asList(includeGroups));
        return this;
    }

    public TestNGOptions excludeGroups(String... excludeGroups) {
        this.excludeGroups.addAll(Arrays.asList(excludeGroups));
        return this;
    }

    public TestNGOptions useDefaultListeners() {
        useDefaultListeners = true;
        return this;
    }

    public TestNGOptions useDefaultListeners(boolean useDefaultListeners) {
        this.useDefaultListeners = useDefaultListeners;
        return this;
    }

    public Object propertyMissing(final String name) {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder.getMetaClass().getProperty(suiteXmlBuilder, name);
        }

        throw new MissingPropertyException(name, getClass());
    }

    public Object methodMissing(String name, Object args) {
        if (suiteXmlBuilder != null) {
            return suiteXmlBuilder.getMetaClass().invokeMethod(suiteXmlBuilder, name, args);
        }

        throw new MissingMethodException(name, getClass(), (Object[]) args);
    }

    /**
     * The location to write TestNG's output. <p> Defaults to the owning test task's location for writing the HTML report.
     *
     * @since 1.11
     */
    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * The set of groups to run.
     */
    @Input
    public Set<String> getIncludeGroups() {
        return includeGroups;
    }

    public void setIncludeGroups(Set<String> includeGroups) {
        this.includeGroups = includeGroups;
    }

    /**
     * The set of groups to exclude.
     */
    @Input
    public Set<String> getExcludeGroups() {
        return excludeGroups;
    }

    public void setExcludeGroups(Set<String> excludeGroups) {
        this.excludeGroups = excludeGroups;
    }

    /**
     * Option for what to do for other tests that use a configuration step when that step fails. Can be "skip" or "continue", defaults to "skip".
     */
    @Internal
    public String getConfigFailurePolicy() {
        return configFailurePolicy;
    }

    public void setConfigFailurePolicy(String configFailurePolicy) {
        this.configFailurePolicy = configFailurePolicy;
    }

    /**
     * Fully qualified classes that are TestNG listeners (instances of org.testng.ITestListener or org.testng.IReporter). By default, the listeners set is empty.
     *
     * Configuring extra listener:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     * }
     *
     * test {
     *     useTestNG() {
     *         // creates emailable HTML file
     *         // this reporter typically ships with TestNG library
     *         listeners &lt;&lt; 'org.testng.reporters.EmailableReporter'
     *     }
     * }
     * </pre>
     */
    @Internal
    public Set<String> getListeners() {
        return listeners;
    }

    public void setListeners(Set<String> listeners) {
        this.listeners = listeners;
    }

    /**
     * The parallel mode to use for running the tests - one of the following modes: methods, tests, classes or instances.
     *
     * Not required.
     *
     * If not present, parallel mode will not be selected
     */
    @Nullable
    @Internal
    public String getParallel() {
        return parallel;
    }

    public void setParallel(String parallel) {
        this.parallel = parallel;
    }

    /**
     * The number of threads to use for this run. Ignored unless the parallel mode is also specified
     */
    @Internal
    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    @Internal
    public boolean getUseDefaultListeners() {
        return useDefaultListeners;
    }

    /**
     * Whether the default listeners and reporters should be used. Since Gradle 1.4 it defaults to 'false' so that Gradle can own the reports generation and provide various improvements. This option
     * might be useful for advanced TestNG users who prefer the reports generated by the TestNG library. If you cannot live without some specific TestNG reporter please use {@link #listeners}
     * property. If you really want to use all default TestNG reporters (e.g. generate the old reports):
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     * }
     *
     * test {
     *     useTestNG() {
     *         // report generation delegated to TestNG library:
     *         useDefaultListeners = true
     *     }
     *
     *     // turn off Gradle's HTML report to avoid replacing the
     *     // reports generated by TestNG library:
     *     reports.html.required = false
     * }
     * </pre>
     *
     * Please refer to the documentation of your version of TestNG what are the default listeners. At the moment of writing this documentation, the default listeners are a set of reporters that
     * generate: TestNG variant of HTML results, TestNG variant of XML results in JUnit format, emailable HTML test report, XML results in TestNG format.
     */
    @Internal
    public boolean isUseDefaultListeners() {
        return useDefaultListeners;
    }

    public void setUseDefaultListeners(boolean useDefaultListeners) {
        this.useDefaultListeners = useDefaultListeners;
    }

    /**
     * Sets the default name of the test suite, if one is not specified in a suite XML file or in the source code.
     */
    @Internal
    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    /**
     * Sets the default name of the test, if one is not specified in a suite XML file or in the source code.
     */
    @Internal
    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    /**
     * The suiteXmlFiles to use for running TestNG.
     *
     * Note: The suiteXmlFiles can be used in conjunction with the suiteXmlBuilder.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public List<File> getSuiteXmlFiles() {
        return suiteXmlFiles;
    }

    public void setSuiteXmlFiles(List<File> suiteXmlFiles) {
        this.suiteXmlFiles = suiteXmlFiles;
    }

    @Internal
    public boolean getPreserveOrder() {
        return preserveOrder;
    }

    /**
     * Indicates whether the tests should be run in deterministic order. Preserving the order guarantees that the complete test
     * (including @BeforeXXX and @AfterXXX) is run in a test thread before the next test is run.
     *
     * Not required.
     *
     * If not present, the order will not be preserved.
     */
    @Internal
    public boolean isPreserveOrder() {
        return preserveOrder;
    }

    public void setPreserveOrder(boolean preserveOrder) {
        this.preserveOrder = preserveOrder;
    }

    @Internal
    public boolean getGroupByInstances() {
        return groupByInstances;
    }

    /**
     * Indicates whether the tests should be grouped by instances. Grouping by instances will result in resolving test method dependencies for each instance instead of running the dependees of all
     * instances before running the dependants.
     *
     * Not required.
     *
     * If not present, the tests will not be grouped by instances.
     */
    @Internal
    public boolean isGroupByInstances() {
        return groupByInstances;
    }

    public void setGroupByInstances(boolean groupByInstances) {
        this.groupByInstances = groupByInstances;
    }

    /**
     * Returns the XML generated using {@link #suiteXmlBuilder()}, if any.
     *
     * <p>This property is read-only and exists merely for up-to-date checking.
     */
    @Input
    @Optional
    protected String getSuiteXml() {
        return suiteXmlWriter == null ? null : suiteXmlWriter.toString();
    }

    @Internal
    public StringWriter getSuiteXmlWriter() {
        return suiteXmlWriter;
    }

    public void setSuiteXmlWriter(StringWriter suiteXmlWriter) {
        this.suiteXmlWriter = suiteXmlWriter;
    }

    @Internal
    public MarkupBuilder getSuiteXmlBuilder() {
        return suiteXmlBuilder;
    }

    public void setSuiteXmlBuilder(MarkupBuilder suiteXmlBuilder) {
        this.suiteXmlBuilder = suiteXmlBuilder;
    }

}
