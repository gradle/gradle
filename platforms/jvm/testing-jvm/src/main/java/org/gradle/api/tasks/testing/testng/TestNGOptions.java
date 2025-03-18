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
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestClassProcessor;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
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
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;
import org.gradle.internal.serialization.Cached;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The TestNG specific test options.
 */
public abstract class TestNGOptions extends TestFrameworkOptions {
    public static final String DEFAULT_CONFIG_FAILURE_POLICY = TestNGTestClassProcessor.DEFAULT_CONFIG_FAILURE_POLICY;
    private static final int DEFAULT_THREAD_COUNT = -1;
    private static final int DEFAULT_SUITE_THREAD_POOL_SIZE_DEFAULT = 1;

    private transient Property<StringWriter> suiteXmlWriter;

    private transient Property<MarkupBuilder> suiteXmlBuilder;

    private final Cached<Provider<String>> cachedSuiteXml = Cached.of(() -> getSuiteXmlWriter().map(StringWriter::toString));

    private final File projectDir;

    @Inject
    public TestNGOptions(ProjectLayout projectLayout, ObjectFactory objects) {
        this.projectDir = projectLayout.getProjectDirectory().getAsFile();
        this.suiteXmlWriter = objects.property(StringWriter.class);
        this.suiteXmlBuilder = objects.property(MarkupBuilder.class);
        this.getSuiteThreadPoolSize().convention(DEFAULT_SUITE_THREAD_POOL_SIZE_DEFAULT);
        this.getTestName().convention("Gradle test");
        this.getSuiteName().convention("Gradle suite");
        this.getUseDefaultListeners().convention(false);
        this.getThreadCount().convention(DEFAULT_THREAD_COUNT);
        this.getConfigFailurePolicy().convention(DEFAULT_CONFIG_FAILURE_POLICY);
        this.getPreserveOrder().convention(false);
        this.getGroupByInstances().convention(false);
    }

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(TestNGOptions other) {
        getOutputDirectory().set(other.getOutputDirectory());
        getIncludeGroups().set(other.getIncludeGroups());
        getExcludeGroups().set(other.getExcludeGroups());
        getConfigFailurePolicy().set(other.getConfigFailurePolicy());
        getListeners().set(other.getListeners());
        getParallel().set(other.getParallel());
        getThreadCount().set(other.getThreadCount());
        getSuiteThreadPoolSize().set(other.getSuiteThreadPoolSize());
        getUseDefaultListeners().set(other.getUseDefaultListeners());
        getThreadPoolFactoryClass().set(other.getThreadPoolFactoryClass());
        getSuiteName().set(other.getSuiteName());
        getTestName().set(other.getTestName());
        getSuiteXmlFiles().setFrom(other.getSuiteXmlFiles());
        getPreserveOrder().set(other.getPreserveOrder());
        getGroupByInstances().set(other.getGroupByInstances());
        // not copying suiteXmlWriter as it is transient
        // not copying suiteXmlBuilder as it is transient
    }

    public MarkupBuilder suiteXmlBuilder() {
        StringWriter suiteXmlWriter = new StringWriter();
        MarkupBuilder markupBuilder = new MarkupBuilder(suiteXmlWriter);
        getSuiteXmlWriter().set(suiteXmlWriter);
        getSuiteXmlBuilder().set(new MarkupBuilder(suiteXmlWriter));
        return markupBuilder;
    }

    /**
     * Add suite files by Strings. Each suiteFile String should be a path relative to the project root.
     */
    public void suites(String... suiteFiles) {
        for (String suiteFile : suiteFiles) {
            getSuiteXmlFiles().from(new File(TestNGOptions.this.getProjectDir(), suiteFile));
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
        getSuiteXmlFiles().from(Arrays.asList(suiteFiles));
    }

    public List<File> getSuites(File testSuitesDir) {
        List<File> suites = new ArrayList<File>();

        suites.addAll(getSuiteXmlFiles().getFiles());

        String suiteXmlMarkup = getSuiteXml().getOrNull();
        if (suiteXmlMarkup != null) {
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
                    writer.write(suiteXmlMarkup);
                }
            });

            suites.add(buildSuiteXml);
        }

        return suites;
    }

    public TestNGOptions includeGroups(String... includeGroups) {
        getIncludeGroups().addAll(includeGroups);
        return this;
    }

    public TestNGOptions excludeGroups(String... excludeGroups) {
        getExcludeGroups().addAll(excludeGroups);
        return this;
    }

    public TestNGOptions useDefaultListeners() {
        return useDefaultListeners(true);
    }

    public TestNGOptions useDefaultListeners(boolean useDefaultListeners) {
        getUseDefaultListeners().set(useDefaultListeners);
        return this;
    }

    public Object propertyMissing(final String name) {
        if (suiteXmlBuilder.getOrNull() != null) {
            return suiteXmlBuilder.get().getMetaClass().getProperty(suiteXmlBuilder, name);
        }

        throw new MissingPropertyException(name, getClass());
    }

    public Object methodMissing(String name, Object args) {
        if (suiteXmlBuilder.getOrNull() != null) {
            return suiteXmlBuilder.get().getMetaClass().invokeMethod(suiteXmlBuilder, name, args);
        }

        throw new MissingMethodException(name, getClass(), (Object[]) args);
    }

    /**
     * The location to write TestNG's output. <p> Defaults to the owning test task's location for writing the HTML report.
     *
     * @since 1.11
     */
    @OutputDirectory
    @ReplacesEagerProperty
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The set of groups to run.
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getIncludeGroups();

    /**
     * The set of groups to exclude.
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getExcludeGroups();

    /**
     * Option for what to do for other tests that use a configuration step when that step fails. Can be "skip" or "continue", defaults to "skip".
     */
    @Internal
    @ReplacesEagerProperty
    public abstract Property<String> getConfigFailurePolicy();

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
     *         listeners.add('org.testng.reporters.EmailableReporter')
     *     }
     * }
     * </pre>
     */
    @Internal
    @ReplacesEagerProperty
    public abstract SetProperty<String> getListeners();

    /**
     * The parallel mode to use for running the tests - one of the following modes: methods, tests, classes or instances.
     *
     * Not required.
     *
     * If not present, parallel mode will not be selected
     */
    @Internal
    @ReplacesEagerProperty
    public abstract Property<String> getParallel();

    /**
     * The number of threads to use for this run. Ignored unless the parallel mode is also specified
     */
    @Internal
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getThreadCount();

    /**
     * The number of XML suites will run parallel
     * @since 8.9
     */
    @Internal
    @Incubating
    public abstract Property<Integer> getSuiteThreadPoolSize();

    /**
     * ThreadPoolExecutorFactory class used by TestNG
     * @since 8.7
     */
    @Internal
    @Incubating
    @ReplacesEagerProperty(
        // Property is marked as incubating, so a change is not reported as a breaking change
        binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT
    )
    public abstract Property<String> getThreadPoolFactoryClass();

    /**
     * Whether the default listeners and reporters should be used. Since Gradle 1.4 it defaults to 'false' so that Gradle can own the reports generation and provide various improvements. This option
     * might be useful for advanced TestNG users who prefer the reports generated by the TestNG library. If you cannot live without some specific TestNG reporter please use {@link #getListeners()}
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "getUseDefaultListeners", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isUseDefaultListeners", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setUseDefaultListeners", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getUseDefaultListeners();

    @Internal
    @Deprecated
    public Property<Boolean> getIsUseDefaultListeners() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsUseDefaultListeners()", "getUseDefaultListeners()");
        return getUseDefaultListeners();
    }

    /**
     * Sets the default name of the test suite, if one is not specified in a suite XML file or in the source code.
     */
    @Internal
    @ReplacesEagerProperty
    public abstract Property<String> getSuiteName();

    /**
     * Sets the default name of the test, if one is not specified in a suite XML file or in the source code.
     */
    @Internal
    @ReplacesEagerProperty
    public abstract Property<String> getTestName();

    /**
     * The suiteXmlFiles to use for running TestNG.
     *
     * Note: The suiteXmlFiles can be used in conjunction with the suiteXmlBuilder.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @ReplacesEagerProperty(adapter = SuiteXmlFilesAdapter.class)
    public abstract ConfigurableFileCollection getSuiteXmlFiles();

    /**
     * Indicates whether the tests should be run in deterministic order. Preserving the order guarantees that the complete test
     * (including @BeforeXXX and @AfterXXX) is run in a test thread before the next test is run.
     *
     * Not required.
     *
     * If not present, the order will not be preserved.
     */
    @Internal
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "getPreserveOrder", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isPreserveOrder", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setPreserveOrder", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getPreserveOrder();

    @Internal
    @Deprecated
    public Property<Boolean> getIsPreserveOrder() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsPreserveOrder()", "getPreserveOrder()");
        return getPreserveOrder();
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "getGroupByInstances", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isGroupByInstances", originalType = boolean.class),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setGroupByInstances", originalType = boolean.class)
        }
    )
    public abstract Property<Boolean> getGroupByInstances();

    @Internal
    @Deprecated
    public Property<Boolean> getIsGroupByInstances() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsGroupByInstances()", "getGroupByInstances()");
        return getGroupByInstances();
    }

    /**
     * Returns the XML generated using {@link #suiteXmlBuilder()}, if any.
     *
     * <p>This property is read-only and exists merely for up-to-date checking.
     */
    @Input
    @Optional
    @ReplacesEagerProperty(adapter = SuiteXmlAdapter.class)
    protected Provider<String> getSuiteXml() {
        return cachedSuiteXml.get();
    }

    @Internal
    @ReplacesEagerProperty
    public Property<StringWriter> getSuiteXmlWriter() {
        return suiteXmlWriter;
    }

    @Internal
    @ReplacesEagerProperty
    public Property<MarkupBuilder> getSuiteXmlBuilder() {
        return suiteXmlBuilder;
    }

    static class SuiteXmlFilesAdapter {
        @BytecodeUpgrade
        static List<File> getSuiteXmlFiles(TestNGOptions options) {
            return new ArrayList<>(options.getSuiteXmlFiles().getFiles());
        }

        @BytecodeUpgrade
        static void setSuiteXmlFiles(TestNGOptions options, List<File> suiteXmlFiles) {
            options.getSuiteXmlFiles().setFrom(suiteXmlFiles);
        }
    }

    static class SuiteXmlAdapter {
        @BytecodeUpgrade
        static String getSuiteXml(TestNGOptions options) {
            return options.getSuiteXml().getOrNull();
        }
    }
}
