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
package org.gradle.api.plugins.quality

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.resources.TextResource

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
class FindBugsExtension extends CodeQualityExtension {
    private final Project prj

    FindBugsExtension(Project project) {
        prj = project
    }

    /**
     * The analysis effort level. The value specified should be one of {@code min}, {@code default}, or {@code max}.
     * Higher levels increase precision and find more bugs at the expense of running time and memory consumption.
     */
    String effort

    /**
     * The priority threshold for reporting bugs. If set to {@code low}, all bugs are reported. If set to
     * {@code medium} (the default), medium and high priority bugs are reported. If set to {@code high},
     * only high priority bugs are reported.
     */
    String reportLevel

    /**
     * The bug detectors which should be run. The bug detectors are specified by their class names,
     * without any package qualification. By default, all detectors which are not disabled by default are run.
     */
    Collection<String> visitors

    /**
     * Similar to {@code visitors} except that it specifies bug detectors which should not be run.
     * By default, no visitors are omitted.
     */
    Collection<String> omitVisitors

    /**
     * A filter specifying which bugs are reported. Replaces the {@code includeFilter} property.
     *
     * @since 2.2
     */
    @Incubating
    TextResource includeFilterConfig

    /**
     * A filter specifying bugs to exclude from being reported. Replaces the {@code excludeFilter} property.
     *
     * @since 2.2
     */
    @Incubating
    TextResource excludeFilterConfig

    /**
     * A filter specifying baseline bugs to exclude from being reported.
     *
     * @since 2.4
     */
    @Incubating
    TextResource excludeBugsFilterConfig

    /**
     * Any additional arguments (not covered here more explicitly like {@code effort}) to be passed along to FindBugs.
     * <p>
     * Extra arguments are passed to FindBugs after the arguments Gradle understands (like {@code effort} but before the list of classes to analyze.
     * This should only be used for arguments that cannot be provided by Gradle directly. Gradle does not try to interpret or validate the arguments
     * before passing them to FindBugs.
     * <p>
     * See the <a href="https://code.google.com/p/findbugs/source/browse/findbugs/src/java/edu/umd/cs/findbugs/TextUICommandLine.java">FindBugs TextUICommandLine source</a> for available options.
     *
     * @since 2.6
     */
    Collection<String> extraArgs

    /**
     * The filename of a filter specifying which bugs are reported.
     */
    File getIncludeFilter() {
        getIncludeFilterConfig()?.asFile()
    }

    /**
     * The filename of a filter specifying which bugs are reported.
     */
    void setIncludeFilter(File filter) {
        setIncludeFilterConfig(prj.resources.text.fromFile(filter))
    }

    /**
     * The filename of a filter specifying bugs to exclude from being reported.
     */
    File getExcludeFilter() {
        getExcludeFilterConfig()?.asFile()
    }

    /**
     * The filename of a filter specifying bugs to exclude from being reported.
     */
    void setExcludeFilter(File filter) {
        setExcludeFilterConfig(prj.resources.text.fromFile(filter))
    }

    /**
     * The filename of a filter specifying baseline bugs to exclude from being reported.
     */
    File getExcludeBugsFilter() {
        getExcludeBugsFilterConfig()?.asFile()
    }

    /**
     * The filename of a filter specifying baseline bugs to exclude from being reported.
     */
    void setExcludeBugsFilter(File filter) {
        setExcludeBugsFilterConfig(prj.resources.text.fromFile(filter))
    }
}
