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
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.internal.PmdAction;
import org.gradle.api.plugins.quality.internal.PmdActionParameters;
import org.gradle.api.plugins.quality.internal.PmdReportsImpl;
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
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.maybeAddOpensJvmArgs;

/**
 * Runs a set of static code analysis rules on Java source code files and generates a report of problems found.
 *
 * @see PmdPlugin
 * @see PmdExtension
 */
@CacheableTask
public abstract class Pmd extends SourceTask implements VerificationTask, Reporting<PmdReports> {

    private FileCollection pmdClasspath;
    private List<String> ruleSets;
    private TargetJdk targetJdk;
    private TextResource ruleSetConfig;
    private FileCollection ruleSetFiles;
    private final PmdReports reports;
    private boolean ignoreFailures;
    private boolean consoleOutput;
    private FileCollection classpath;
    private final Property<Integer> rulesMinimumPriority;
    private final Property<Integer> maxFailures;
    private final Property<Boolean> incrementalAnalysis;
    private final Property<Integer> threads;
    private final Property<JavaLauncher> javaLauncher;

    public Pmd() {
        ObjectFactory objects = getObjectFactory();
        reports = objects.newInstance(PmdReportsImpl.class, this);
        this.rulesMinimumPriority = objects.property(Integer.class);
        this.incrementalAnalysis = objects.property(Boolean.class);
        this.maxFailures = objects.property(Integer.class);
        this.threads = objects.property(Integer.class);
        // Set default JavaLauncher to current JVM in case
        // PmdPlugin that sets Java launcher convention is not applied
        this.javaLauncher = configureFromCurrentJvmLauncher(getToolchainService(), getObjectFactory());
    }

    private static Property<JavaLauncher> configureFromCurrentJvmLauncher(JavaToolchainService toolchainService, ObjectFactory objectFactory) {
        Provider<JavaLauncher> currentJvmLauncherProvider = toolchainService.launcherFor(new CurrentJvmToolchainSpec(objectFactory));
        return objectFactory.property(JavaLauncher.class).convention(currentJvmLauncherProvider);
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerExecutor getWorkerExecutor() {
        throw new UnsupportedOperationException();
    }

    /**
     * JavaLauncher for toolchain support
     * @since 8.0
     */
    @Incubating
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @TaskAction
    public void run() {
        validate(rulesMinimumPriority.get());
        validateThreads(threads.get());

        WorkQueue workQueue = getWorkerExecutor().processIsolation(spec -> {
            spec.getForkOptions().setExecutable(javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath());
            maybeAddOpensJvmArgs(javaLauncher.get(), spec);
        });
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
        parameters.getConsoleOutput().set(isConsoleOutput());
        parameters.getStdOutIsAttachedToTerminal().set(stdOutIsAttachedToTerminal());
        if (getClasspath() != null) {
            parameters.getAuxClasspath().setFrom(getClasspath());
        }
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
    private void validateThreads(int value) {
        if (value < 0) {
            throw new InvalidUserDataException(String.format("Invalid number of threads '%d'.  Number should not be negative.", value));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The class path containing the PMD library to be used.
     */
    @Classpath
    public FileCollection getPmdClasspath() {
        return pmdClasspath;
    }

    /**
     * The class path containing the PMD library to be used.
     */
    public void setPmdClasspath(FileCollection pmdClasspath) {
        this.pmdClasspath = pmdClasspath;
    }

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * <pre>
     *     ruleSets = ["basic", "braces"]
     * </pre>
     */
    @Input
    public List<String> getRuleSets() {
        return ruleSets;
    }

    /**
     * The built-in rule sets to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_rules_java.html">official list</a> of built-in rule sets.
     *
     * <pre>
     *     ruleSets = ["basic", "braces"]
     * </pre>
     */
    public void setRuleSets(List<String> ruleSets) {
        this.ruleSets = ruleSets;
    }

    /**
     * The target JDK to use with PMD.
     */
    @Input
    public TargetJdk getTargetJdk() {
        return targetJdk;
    }

    /**
     * The target JDK to use with PMD.
     */
    public void setTargetJdk(TargetJdk targetJdk) {
        this.targetJdk = targetJdk;
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
    public FileCollection getRuleSetFiles() {
        return ruleSetFiles;
    }

    /**
     * The custom rule set files to be used. See the <a href="https://docs.pmd-code.org/pmd-doc-6.55.0/pmd_userdocs_making_rulesets.html">official documentation</a> for how to author a rule set file.
     * This adds to the default rule sets defined by {@link #getRuleSets()}.
     *
     * <pre>
     *     ruleSetFiles = files("config/pmd/myRuleSets.xml")
     * </pre>
     */
    public void setRuleSetFiles(FileCollection ruleSetFiles) {
        this.ruleSetFiles = ruleSetFiles;
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final PmdReports getReports() {
        return reports;
    }

    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * <pre>
     *     ignoreFailures = true
     * </pre>
     */
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }


    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * <pre>
     *     ignoreFailures = true
     * </pre>
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
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
    public boolean isConsoleOutput() {
        return consoleOutput;
    }

    /**
     * Whether or not to write PMD results to {@code System.out}.
     *
     * @since 2.1
     */
    public void setConsoleOutput(boolean consoleOutput) {
        this.consoleOutput = consoleOutput;
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
    @Nullable
    @Optional
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
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
    public void setClasspath(@Nullable FileCollection classpath) {
        this.classpath = classpath;
    }

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
    public File getIncrementalCacheFile() {
        return new File(getTemporaryDir(), "incremental.cache");
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
