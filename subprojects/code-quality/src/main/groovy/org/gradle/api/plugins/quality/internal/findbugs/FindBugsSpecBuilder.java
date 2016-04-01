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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.quality.FindBugsReports;
import org.gradle.api.reporting.internal.CustomizableHtmlReportImpl;
import org.gradle.api.plugins.quality.internal.FindBugsReportsImpl;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class FindBugsSpecBuilder {
    private static final Set<String> VALID_EFFORTS = ImmutableSet.of("min", "default", "max");
    private static final Set<String> VALID_REPORT_LEVELS = ImmutableSet.of("experimental", "low", "medium", "high");

    private FileCollection pluginsList;
    private FileCollection sources;
    private FileCollection classpath;
    private FileCollection classes;
    private FindBugsReports reports;

    private String effort;
    private String reportLevel;
    private String maxHeapSize;
    private Collection<String> visitors;
    private Collection<String> omitVisitors;
    private File excludeFilter;
    private File includeFilter;
    private File excludeBugsFilter;
    private Collection<String> extraArgs;
    private boolean debugEnabled;

    public FindBugsSpecBuilder(FileCollection classes) {
        if(classes == null || classes.isEmpty()){
            throw new InvalidUserDataException("No classes configured for FindBugs analysis.");
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

    public FindBugsSpecBuilder configureReports(FindBugsReports reports) {
        this.reports = reports;
        return this;
    }


    public FindBugsSpecBuilder withEffort(String effort) {
        if (effort != null && !VALID_EFFORTS.contains(effort)) {
            throw new InvalidUserDataException("Invalid value for FindBugs 'effort' property: " + effort);
        }
        this.effort = effort;
        return this;
    }

    public FindBugsSpecBuilder withReportLevel(String reportLevel) {
        if (reportLevel != null && !VALID_REPORT_LEVELS.contains(reportLevel)) {
            throw new InvalidUserDataException("Invalid value for FindBugs 'reportLevel' property: " + reportLevel);
        }
        this.reportLevel = reportLevel;
        return this;
    }

    public FindBugsSpecBuilder withMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
        return this;
    }

    public FindBugsSpecBuilder withVisitors(Collection<String> visitors) {
        this.visitors = visitors;
        return this;
    }

    public FindBugsSpecBuilder withOmitVisitors(Collection<String> omitVisitors) {
        this.omitVisitors = omitVisitors;
        return this;
    }

    public FindBugsSpecBuilder withExcludeFilter(File excludeFilter) {
        if (excludeFilter != null && !excludeFilter.canRead()) {
            String errorStr = String.format("Cannot read file specified for FindBugs 'excludeFilter' property: %s", excludeFilter);
            throw new InvalidUserDataException(errorStr);
        }

        this.excludeFilter = excludeFilter;
        return this;
    }

    public FindBugsSpecBuilder withIncludeFilter(File includeFilter) {
        if (includeFilter != null && !includeFilter.canRead()) {
            String errorStr = String.format("Cannot read file specified for FindBugs 'includeFilter' property: %s", includeFilter);
            throw new InvalidUserDataException(errorStr);
        }

        this.includeFilter = includeFilter;
        return this;
    }

    public FindBugsSpecBuilder withExcludeBugsFilter(File excludeBugsFilter) {
        if (excludeBugsFilter != null && !excludeBugsFilter.canRead()) {
            String errorStr = String.format("Cannot read file specified for FindBugs 'excludeBugsFilter' property: %s", excludeBugsFilter);
            throw new InvalidUserDataException(errorStr);
        }

        this.excludeBugsFilter = excludeBugsFilter;

        return this;
    }

    public FindBugsSpecBuilder withExtraArgs(Collection<String> extraArgs) {
        this.extraArgs = extraArgs;
        return this;
    }

    public FindBugsSpecBuilder withDebugging(boolean debugEnabled){
        this.debugEnabled = debugEnabled;
        return this;
    }

    public FindBugsSpec build() {
        ArrayList<String> args = new ArrayList<String>();
        args.add("-pluginList");
        args.add(pluginsList==null ? "" : pluginsList.getAsPath());
        args.add("-sortByClass");
        args.add("-timestampNow");
        args.add("-progress");

        if (reports != null && !reports.getEnabled().isEmpty()) {
            if (reports.getEnabled().size() == 1) {
                FindBugsReportsImpl reportsImpl = (FindBugsReportsImpl) reports;
                String outputArg = "-" + reportsImpl.getFirstEnabled().getName();
                if (reportsImpl.getFirstEnabled() instanceof FindBugsXmlReportImpl) {
                    FindBugsXmlReportImpl r = (FindBugsXmlReportImpl) reportsImpl.getFirstEnabled();
                    if (r.isWithMessages()) {
                        outputArg += ":withMessages";
                    }
                } else if (reportsImpl.getFirstEnabled() instanceof CustomizableHtmlReportImpl) {
                    CustomizableHtmlReportImpl r = (CustomizableHtmlReportImpl) reportsImpl.getFirstEnabled();
                    if (r.getStylesheet() != null) {
                        outputArg += ':' + r.getStylesheet().asFile().getAbsolutePath();
                    }
                }
                args.add(outputArg);
                args.add("-outputFile");
                args.add(reportsImpl.getFirstEnabled().getDestination().getAbsolutePath());
            } else {
                throw new InvalidUserDataException("FindBugs tasks can only have one report enabled, however more than one report was enabled. You need to disable all but one of them.");
            }
        }

        if (has(sources)) {
            args.add("-sourcepath");
            args.add(sources.getAsPath());
        }

        if (has(classpath)) {
            args.add("-auxclasspath");

            // Filter unexisting files as FindBugs can't handle them.
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
            args.add(CollectionUtils.join(",", visitors));
        }

        if (has(omitVisitors)) {
            args.add("-omitVisitors");
            args.add(CollectionUtils.join(",", omitVisitors));
        }

        if (has(excludeFilter)) {
            args.add("-exclude");
            args.add(excludeFilter.getPath());
        }

        if (has(includeFilter)) {
            args.add("-include");
            args.add(includeFilter.getPath());
        }

        if (has(excludeBugsFilter)) {
            args.add("-excludeBugs");
            args.add(excludeBugsFilter.getPath());
        }

        if (has(extraArgs)) {
            args.addAll(extraArgs);
        }

        for (File classFile : classes.getFiles()) {
            args.add(classFile.getAbsolutePath());
        }

        return new FindBugsSpec(args, maxHeapSize, debugEnabled);
    }

    private boolean has(String str) {
        return str != null && str.length() > 0;
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
