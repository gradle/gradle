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

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.resources.TextResource;

import java.io.File;
import java.util.Collection;

/**
 * Configuration options for the FindBugs plugin. All options have sensible defaults.
 * See the <a href="http://findbugs.sourceforge.net/manual/">FindBugs Manual</a> for additional information
 * on these options.
 *
 * <p>Below is a full configuration example. Since all properties have sensible defaults,
 * typically only selected properties will be configured.
 *
 * <pre autoTested=''>
 *     apply plugin: "java"
 *     apply plugin: "findbugs"
 *
 *     findbugs {
 *         toolVersion = "2.0.1"
 *         sourceSets = [sourceSets.main]
 *         ignoreFailures = true
 *         reportsDir = file("$project.buildDir/findbugsReports")
 *         effort = "max"
 *         reportLevel = "high"
 *         visitors = ["FindSqlInjection", "SwitchFallthrough"]
 *         omitVisitors = ["FindNonShortCircuit"]
 *         includeFilter = file("$rootProject.projectDir/config/findbugs/includeFilter.xml")
 *         excludeFilter = file("$rootProject.projectDir/config/findbugs/excludeFilter.xml")
 *         excludeBugsFilter = file("$rootProject.projectDir/config/findbugs/excludeBugsFilter.xml")
 *     }
 * </pre>
 *
 * @see FindBugsPlugin
 */
public class FindBugsExtension extends CodeQualityExtension {

    private final Project project;

    private String effort;
    private String reportLevel;
    private Collection<String> visitors;
    private Collection<String> omitVisitors;
    private TextResource includeFilterConfig;
    private TextResource excludeFilterConfig;
    private TextResource excludeBugsFilterConfig;
    private Collection<String> extraArgs;

    public FindBugsExtension(Project project) {
        this.project = project;
    }

    /**
     * The analysis effort level.
     * The value specified should be one of {@code min}, {@code default}, or {@code max}.
     * Higher levels increase precision and find more bugs at the expense of running time and memory consumption.
     */
    public String getEffort() {
        return effort;
    }

    public void setEffort(String effort) {
        this.effort = effort;
    }

    /**
     * The priority threshold for reporting bugs.
     * If set to {@code low}, all bugs are reported.
     * If set to {@code medium} (the default), medium and high priority bugs are reported.
     * If set to {@code high}, only high priority bugs are reported.
     */
    public String getReportLevel() {
        return reportLevel;
    }

    public void setReportLevel(String reportLevel) {
        this.reportLevel = reportLevel;
    }

    /**
     * The bug detectors which should be run.
     * The bug detectors are specified by their class names, without any package qualification.
     * By default, all detectors which are not disabled by default are run.
     */
    public Collection<String> getVisitors() {
        return visitors;
    }

    public void setVisitors(Collection<String> visitors) {
        this.visitors = visitors;
    }

    /**
     * Similar to {@code visitors} except that it specifies bug detectors which should not be run.
     * By default, no visitors are omitted.
     */
    public Collection<String> getOmitVisitors() {
        return omitVisitors;
    }

    public void setOmitVisitors(Collection<String> omitVisitors) {
        this.omitVisitors = omitVisitors;
    }

    /**
     * A filter specifying which bugs are reported. Replaces the {@code includeFilter} property.
     *
     * @since 2.2
     */
    @Incubating
    public TextResource getIncludeFilterConfig() {
        return includeFilterConfig;
    }

    @Incubating
    public void setIncludeFilterConfig(TextResource includeFilterConfig) {
        this.includeFilterConfig = includeFilterConfig;
    }

    /**
     * The filename of a filter specifying which bugs are reported.
     */
    public File getIncludeFilter() {
        TextResource includeFilterConfig = getIncludeFilterConfig();
        if (includeFilterConfig == null) {
            return null;
        }
        return includeFilterConfig.asFile();
    }

    /**
     * The filename of a filter specifying which bugs are reported.
     */
    public void setIncludeFilter(File filter) {
        setIncludeFilterConfig(project.getResources().getText().fromFile(filter));
    }

    /**
     * A filter specifying bugs to exclude from being reported. Replaces the {@code excludeFilter} property.
     *
     * @since 2.2
     */
    @Incubating
    public TextResource getExcludeFilterConfig() {
        return excludeFilterConfig;
    }

    @Incubating
    public void setExcludeFilterConfig(TextResource excludeFilterConfig) {
        this.excludeFilterConfig = excludeFilterConfig;
    }

    /**
     * The filename of a filter specifying bugs to exclude from being reported.
     */
    public File getExcludeFilter() {
        TextResource excludeFilterConfig = getExcludeFilterConfig();
        if (excludeFilterConfig == null) {
            return null;
        }
        return excludeFilterConfig.asFile();
    }

    /**
     * The filename of a filter specifying bugs to exclude from being reported.
     */
    public void setExcludeFilter(File filter) {
        setExcludeFilterConfig(project.getResources().getText().fromFile(filter));
    }

    /**
     * A filter specifying baseline bugs to exclude from being reported.
     *
     * @since 2.4
     */
    @Incubating
    public TextResource getExcludeBugsFilterConfig() {
        return excludeBugsFilterConfig;
    }

    @Incubating
    public void setExcludeBugsFilterConfig(TextResource excludeBugsFilterConfig) {
        this.excludeBugsFilterConfig = excludeBugsFilterConfig;
    }

    /**
     * The filename of a filter specifying baseline bugs to exclude from being reported.
     */
    public File getExcludeBugsFilter() {
        TextResource excludeBugsFilterConfig = getExcludeBugsFilterConfig();
        if (excludeBugsFilterConfig == null) {
            return null;
        }
        return excludeBugsFilterConfig.asFile();
    }

    /**
     * The filename of a filter specifying baseline bugs to exclude from being reported.
     */
    public void setExcludeBugsFilter(File filter) {
        setExcludeBugsFilterConfig(project.getResources().getText().fromFile(filter));
    }

    /**
     * Any additional arguments (not covered here more explicitly like {@code effort}) to be passed along to FindBugs.
     * <p>
     * Extra arguments are passed to FindBugs after the arguments Gradle understands (like {@code effort} but before the list of classes to analyze.
     * This should only be used for arguments that cannot be provided by Gradle directly.
     * Gradle does not try to interpret or validate the arguments before passing them to FindBugs.
     * <p>
     * See the <a href="https://code.google.com/p/findbugs/source/browse/findbugs/src/java/edu/umd/cs/findbugs/TextUICommandLine.java">FindBugs
     * TextUICommandLine source</a> for available options.
     *
     * @since 2.6
     */
    public Collection<String> getExtraArgs() {
        return extraArgs;
    }

    public void setExtraArgs(Collection<String> extraArgs) {
        this.extraArgs = extraArgs;
    }
}
