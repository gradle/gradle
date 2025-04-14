/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.quality.internal.CodeNarcAction;
import org.gradle.api.plugins.quality.internal.CodeNarcActionParameters;
import org.gradle.api.plugins.quality.internal.CodeNarcReportsImpl;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Describables;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkQueue;

import java.io.File;
import java.util.stream.Collectors;

/**
 * Runs CodeNarc against some source files.
 */
@CacheableTask
public abstract class CodeNarc extends AbstractCodeQualityTask implements Reporting<CodeNarcReports> {

    private final CodeNarcReports reports;
    private FileCollection codenarcClasspath;
    private FileCollection compilationClasspath;
    private TextResource config;
    private int maxPriority1Violations;
    private int maxPriority2Violations;
    private int maxPriority3Violations;

    public CodeNarc() {
        super();
        reports = getObjectFactory().newInstance(CodeNarcReportsImpl.class, Describables.quoted("Task", getIdentityPath()));
        compilationClasspath = getProject().files();
        // Set default JavaLauncher to current JVM in case
        // CodeNarcPlugin that sets Java launcher convention is not applied
    }

    /**
     * The CodeNarc configuration file to use.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public File getConfigFile() {
        return getConfig() == null ? null : getConfig().asFile();
    }

    /**
     * The CodeNarc configuration file to use.
     */
    public void setConfigFile(File configFile) {
        setConfig(getProject().getResources().getText().fromFile(configFile));
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

    @TaskAction
    public void run() {
        WorkQueue workQueue = getWorkerExecutor().processIsolation(spec -> configureForkOptions(spec.getForkOptions()));
        workQueue.submit(CodeNarcAction.class, this::setupParameters);
    }

    private void setupParameters(CodeNarcActionParameters parameters) {
        parameters.getAntLibraryClasspath().setFrom(getCodenarcClasspath());
        parameters.getCompilationClasspath().setFrom(getCompilationClasspath());
        parameters.getConfig().set(getConfigFile());
        parameters.getMaxPriority1Violations().set(getMaxPriority1Violations());
        parameters.getMaxPriority2Violations().set(getMaxPriority2Violations());
        parameters.getMaxPriority3Violations().set(getMaxPriority3Violations());
        parameters.getEnabledReports().set(getReports().getEnabled().stream().map(report -> {
            CodeNarcActionParameters.EnabledReport newReport = getObjectFactory().newInstance(CodeNarcActionParameters.EnabledReport.class);
            newReport.getName().set(report.getName());
            newReport.getOutputLocation().set(report.getOutputLocation());
            return newReport;
        }).collect(Collectors.toList()));
        parameters.getIgnoreFailures().set(getIgnoreFailures());
        parameters.getSource().setFrom(getSource());
    }

    /**
     * Configures the reports to be generated by this task.
     */
    @Override
    public CodeNarcReports reports(@SuppressWarnings("rawtypes") Closure closure) {
        return reports(new ClosureBackedAction<CodeNarcReports>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     */
    @Override
    public CodeNarcReports reports(Action<? super CodeNarcReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    /**
     * The class path containing the CodeNarc library to be used.
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getCodenarcClasspath() {
        return codenarcClasspath;
    }

    /**
     * The class path containing the CodeNarc library to be used.
     */
    public void setCodenarcClasspath(FileCollection codenarcClasspath) {
        this.codenarcClasspath = codenarcClasspath;
    }

    /**
     * The class path to be used by CodeNarc when compiling classes during analysis.
     *
     * @since 4.2
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getCompilationClasspath() {
        return compilationClasspath;
    }

    /**
     * The class path to be used by CodeNarc when compiling classes during analysis.
     *
     * @since 4.2
     */
    public void setCompilationClasspath(FileCollection compilationClasspath) {
        this.compilationClasspath = compilationClasspath;
    }

    /**
     * The CodeNarc configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Nested
    public TextResource getConfig() {
        return config;
    }

    /**
     * The CodeNarc configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    public void setConfig(TextResource config) {
        this.config = config;
    }

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public int getMaxPriority1Violations() {
        return maxPriority1Violations;
    }

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    public void setMaxPriority1Violations(int maxPriority1Violations) {
        this.maxPriority1Violations = maxPriority1Violations;
    }

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public int getMaxPriority2Violations() {
        return maxPriority2Violations;
    }

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    public void setMaxPriority2Violations(int maxPriority2Violations) {
        this.maxPriority2Violations = maxPriority2Violations;
    }

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public int getMaxPriority3Violations() {
        return maxPriority3Violations;
    }

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    public void setMaxPriority3Violations(int maxPriority3Violations) {
        this.maxPriority3Violations = maxPriority3Violations;
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public CodeNarcReports getReports() {
        return reports;
    }

}
