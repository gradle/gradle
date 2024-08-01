/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.internal.PmdAction;
import org.gradle.api.plugins.quality.internal.PmdActionParameters;
import org.gradle.api.plugins.quality.internal.PmdReportsImpl;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Describables;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkQueue;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

/**
 * Runs a set of static code analysis rules on Java source code files and generates a report of problems found.
 *
 * @see PmdPlugin
 * @see PmdExtension
 */
@CacheableTask
public abstract class Pmd extends AbstractCodeQualityTask implements Reporting<PmdReports> {

    private TextResource ruleSetConfig;
    private final PmdReports reports;
    private final Property<Integer> rulesMinimumPriority;
    private final Property<Integer> maxFailures;
    private final Property<Boolean> incrementalAnalysis;
    private final Property<Integer> threads;
    private final Property<RegularFile> incrementalCacheFile;

    public Pmd() {
        super();
        ObjectFactory objects = getObjectFactory();
        reports = objects.newInstance(PmdReportsImpl.class, Describables.quoted("Task", getIdentityPath()));
        this.rulesMinimumPriority = objects.property(Integer.class);
        this.incrementalAnalysis = objects.property(Boolean.class);
        this.maxFailures = objects.property(Integer.class);
        this.threads = objects.property(Integer.class);
        getConsoleOutput().convention(false);

        DirectoryProperty temporaryDir = objects.directoryProperty().fileProvider(getProject().provider(this::getTemporaryDir));
        this.incrementalCacheFile = objects.fileProperty().convention(temporaryDir.file("incremental.cache"));
    }

    @TaskAction
    public void run() {
        validate(rulesMinimumPriority.get());
        validateThreads(threads.get());

        WorkQueue workQueue = getWorkerExecutor().processIsolation(spec -> configureForkOptions(spec.getForkOptions()));
        workQueue.submit(PmdAction.class, this::setupParameters);
    }

    private void setupParameters(PmdActionParameters parameters) {
        parameters.getAntLibraryClasspath().setFrom(getPmdClasspath());
        parameters.getPmdClasspath().setFrom(getPmdClasspath());
        parameters.getTargetJdk().set(getTargetJdk());
        parameters.getRuleSets().set(getRuleSets());
        parameters.getRuleSetConfigFiles().from(getRuleSetFiles());
        if (getRuleSetConfig() != null) {
            parameters.getRuleSetConfigFiles().from(getRuleSetConfig().asFile());
        }
        parameters.getIgnoreFailures().set(getIgnoreFailures());
        parameters.getConsoleOutput().set(getConsoleOutput());
        parameters.getStdOutIsAttachedToTerminal().set(stdOutIsAttachedToTerminal());
        parameters.getAuxClasspath().setFrom(getClasspath());
        parameters.getRulesMinimumPriority().set(getRulesMinimumPriority());
        parameters.getMaxFailures().set(getMaxFailures());
        parameters.getIncrementalAnalysis().set(getIncrementalAnalysis());
        parameters.getIncrementalCacheFile().set(getIncrementalCacheFile());
        parameters.getThreads().set(getThreads());
        parameters.getSource().setFrom(getSource());
        parameters.getEnabledReports().set(getReports().getEnabled().stream().map(report -> {
            PmdActionParameters.EnabledReport newReport = getObjectFactory().newInstance(PmdActionParameters.EnabledReport.class);
            newReport.getName().set(report.getName());
            newReport.getOutputLocation().set(report.getOutputLocation());
            return newReport;
        }).collect(Collectors.toList()));
    }

    public boolean stdOutIsAttachedToTerminal() {
        try {
            ConsoleDetector consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
            ConsoleMetaData consoleMetaData = consoleDetector.getConsole();
            return consoleMetaData != null && consoleMetaData.isStdOut();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Configures the reports to be generated by this task.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public PmdReports reports(@DelegatesTo(value = PmdReports.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * @since 3.0
     */
    @Override
    public PmdReports reports(Action<? super PmdReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    /**
     * Validates the value is a valid PMD rules minimum priority (1-5)
     *
     * @param value rules minimum priority threshold
     */
    public static void validate(int value) {
        if (value > 5 || value < 1) {
            throw new InvalidUserDataException(String.format("Invalid rulesMinimumPriority '%d'.  Valid range 1 (highest) to 5 (lowest).", value));
        }
    }

    /**
     * Validates the number of threads used by PMD.
     *
     * @param value the number of threads used by PMD
     */
    private static void validateThreads(int value) {
        if (value < 0) {
            throw new InvalidUserDataException(String.format("Invalid number of threads '%d'.  Number should not be negative.", value));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The class path containing the PMD library to be used.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getPmdClasspath();

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * <pre>
     *     ruleSets = ["basic", "braces"]
     * </pre>
     */
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getRuleSets();

    /**
     * The target JDK to use with PMD.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<TargetJdk> getTargetJdk();

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
     *
     * <pre>
     *     ruleSetConfig = resources.text.fromFile(resources.file("config/pmd/myRuleSets.xml"))
     * </pre>
     *
     * @since 2.2
     */
    @Nullable
    @Optional
    @Nested
    public TextResource getRuleSetConfig() {
        return ruleSetConfig;
    }

    /**
     * The custom rule set to be used (if any). Replaces {@code ruleSetFiles}, except that it does not currently support multiple rule sets.
     *
     * See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set.
     *
     * <pre>
     *     ruleSetConfig = resources.text.fromFile(resources.file("config/pmd/myRuleSets.xml"))
     * </pre>
     *
     * @since 2.2
     */
    public void setRuleSetConfig(@Nullable TextResource ruleSetConfig) {
        this.ruleSetConfig = ruleSetConfig;
    }

    /**
     * The custom rule set files to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set file.
     * If you want to only use custom rule sets, you must clear {@code ruleSets}.
     *
     * <pre>
     *     ruleSetFiles = files("config/pmd/myRuleSet.xml")
     * </pre>
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getRuleSetFiles();

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final PmdReports getReports() {
        return reports;
    }

    /**
     * The maximum number of failures to allow before stopping the build.
     *
     * Defaults to 0, which will stop the build on any failure.  Values 0 and
     * above are valid.  If <pre>ignoreFailures</pre> is set, this is ignored
     * and the build will continue (infinite failures allowed).
     *
     * @since 6.4
     */
    @Input
    public Property<Integer> getMaxFailures() {
        return maxFailures;
    }

    /**
     * Specifies the rule priority threshold.
     *
     * @see PmdExtension#getRulesMinimumPriority()
     * @since 6.8
     */
    @Input
    public Property<Integer> getRulesMinimumPriority() {
        return rulesMinimumPriority;
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     *
     * @since 2.1
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getConsoleOutput();

    @Internal
    @Deprecated
    public Property<Boolean> getIsConsoleOutput() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsConsoleOutput()", "getConsoleOutput()");
        return getConsoleOutput();
    }

    /**
     * Compile class path for the classes to be analyzed.
     *
     * The classes on this class path are used during analysis but aren't analyzed themselves.
     *
     * This is only well supported for PMD 5.2.1 or better.
     *
     * @since 2.8
     */
    @Optional
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Controls whether to use incremental analysis or not.
     *
     * This is only supported for PMD 6.0.0 or better. See <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_incremental_analysis.html"></a> for more details.
     *
     * @since 5.6
     */
    @Internal
    public Property<Boolean> getIncrementalAnalysis() {
        return incrementalAnalysis;
    }

    /**
     * Path to the incremental cache file, if incremental analysis is used.
     *
     * @since 5.6
     */
    @LocalState
    @ReplacesEagerProperty
    public Provider<RegularFile> getIncrementalCacheFile() {
        return incrementalCacheFile;
    }

    /**
     * Specifies the number of threads used by PMD.
     *
     * @see PmdExtension#getThreads()
     * @since 7.5
     */
    @Input
    public Property<Integer> getThreads() {
        return threads;
    }
}
