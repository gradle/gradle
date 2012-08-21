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

package org.gradle.api.plugins.quality.internal.findbugs;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.quality.FindBugsReports;
import org.gradle.api.plugins.quality.internal.FindBugsReportsImpl;
import org.gradle.api.specs.Spec;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;

import com.google.common.collect.Sets;

public class FindBugsSpecBuilder {
    private FileCollection pluginsList;
    private FileCollection sources;
    private FileCollection classpath;

    private ArrayList<String> args;
    private FileCollection classes;
    private FindBugsReports reports;

    private String effort;
    private String reportLevel;
    private Collection<String> visitors;
    private Collection<String> omitVisitors;
    private File excludeFilter;
    private File includeFilter;
    private boolean debugEnabled;

    public FindBugsSpecBuilder(FileCollection classes) {
        if(classes == null || classes.isEmpty()){
            throw new InvalidUserDataException("Classes must be configured to be analyzed by the Findbugs.");
        }
        this.classes = classes;
    }

    public FindBugsSpecBuilder withPluginsList(FileCollection pluginsClasspath) {
        this.pluginsList = pluginsClasspath;
        return this;
    }

    public FindBugsSpecBuilder withSources(FileCollection sources) {
        this.sources = sources;
        return this;
    }

    public FindBugsSpecBuilder withClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    public FindBugsSpecBuilder configureReports(FindBugsReports reports){
        this.reports = reports;
        return this;
    }

    /**
     * Valid values for Effort
     */
    private Set<String> validEfforts = Sets.newHashSet("min", "default", "max");

    public FindBugsSpecBuilder withEffort(String effort){
        // Enum-like values, they need to be validated against a set of possible values.
        if (effort != null) {
            if (!validEfforts.contains(effort)) {
                String validEffortsStr = GUtil.join(validEfforts, "\", \"");
                String errorStr = String.format("FindBugs encountered an improper value (%s) for effort property , should be one of \"%s\"", effort, validEffortsStr);
                throw new InvalidUserDataException(errorStr);
            }
            this.effort = effort;
        }
        return this;
    }

    /**
     * Valid values for reportLevel
     */
    private Set<String> validReportLevels = Sets.newHashSet("experimental", "low", "medium", "high");

    public FindBugsSpecBuilder withReportLevel(String reportLevel){
        if (reportLevel != null) {
            if (!validReportLevels.contains(reportLevel)) {
                String validReportLevelsStr = GUtil.join(validReportLevels, "\", \"");
                String errorStr = String.format("FindBugs encountered an improper value (%s) for reportLevel property , should be one of \"%s\"", reportLevel, validReportLevelsStr);
                throw new InvalidUserDataException(errorStr);
            }
            this.reportLevel = reportLevel;
        }
        return this;
    }

    public FindBugsSpecBuilder withVisitors(Collection<String> visitors) {
        if (visitors != null) {
            // Remove commas
            this.visitors = visitors;
        }
        return this;
    }

    public FindBugsSpecBuilder withOmitVisitors(Collection<String> omitVisitors) {
        this.omitVisitors = omitVisitors;
        return this;
    }

    public FindBugsSpecBuilder withExcludeFilter(File excludeFilter) {
        if (excludeFilter != null && !excludeFilter.canRead()) {
            String errorStr = String.format("FindBugs encountered an improper value (%s) for excludeFilter property , can not be read", excludeFilter);
            throw new InvalidUserDataException(errorStr);
        }

        this.excludeFilter = excludeFilter;
        return this;
    }

    public FindBugsSpecBuilder withIncludeFilter(File includeFilter) {
        if (includeFilter != null && !includeFilter.canRead()) {
            String errorStr = String.format("FindBugs encountered an improper value (%s) for includeFilter property , can not be read", includeFilter);
            throw new InvalidUserDataException(errorStr);
        }

        this.includeFilter = includeFilter;
        return this;
    }

    public FindBugsSpecBuilder withDebugging(boolean debugEnabled){
        this.debugEnabled = debugEnabled;
        return this;
    }

    public FindBugsSpec build() {
        args = new ArrayList<String>();
        args.add("-pluginList");
        args.add(pluginsList==null ? "" : pluginsList.getAsPath());
        args.add("-sortByClass");
        args.add("-timestampNow");
        args.add("-progress");

        if (reports != null && !reports.getEnabled().isEmpty()) {
            if (reports.getEnabled().size() == 1) {
                FindBugsReportsImpl reportsImpl = (FindBugsReportsImpl) reports;
                args.add("-" + reportsImpl.getFirstEnabled().getName());
                args.add("-outputFile");
                args.add(reportsImpl.getFirstEnabled().getDestination().getAbsolutePath());
            } else {
                throw new InvalidUserDataException("Findbugs tasks can only have one report enabled, however both the xml and html report are enabled. You need to disable one of them.");
            }
        }

        if (has(sources)) {
            args.add("-sourcepath");
            args.add(sources.getAsPath());
        }

        if (has(classpath)) {
            args.add("-auxclasspath");

            // Filter unexisting files as findbugs can't handle them.
            args.add(classpath.filter(new Spec<File>() {
                public boolean isSatisfiedBy(File element) {
                    return element.exists();
                }
            }).getAsPath());
        }

        if (has(effort)) {
            args.add(String.format("-effort:%s", effort));
        }

        if (has(reportLevel)) {
            args.add(String.format("-%s", reportLevel));
        }

        if (has(visitors)) {
            args.add("-visitors");
            args.add(GUtil.join(visitors, ","));
        }

        if (has(omitVisitors)) {
            args.add("-omitVisitors");
            args.add(GUtil.join(omitVisitors, ","));
        }

        if (has(excludeFilter)) {
            args.add("-exclude");
            args.add(excludeFilter.getPath());
        }

        if (has(includeFilter)) {
            args.add("-include");
            args.add(includeFilter.getPath());
        }

        for (File classFile : classes.getFiles()) {
            args.add(classFile.getAbsolutePath());
        }
        FindBugsSpec spec = new FindBugsSpec(args, debugEnabled);
        return spec;
    }

    private boolean has(String str) {
        return str != null && !str.isEmpty();
    }

    private boolean has(File file) {
        return file != null && file.canRead();
    }

    private boolean has(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    private boolean has(FileCollection fileCollection) {
        return fileCollection != null && !fileCollection.isEmpty();
    }
}
