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

import java.io.File;
import java.util.ArrayList;

public class FindBugsSpecBuilder {
    private FileCollection pluginsList;
    private FileCollection sources;
    private FileCollection classpath;

    private ArrayList<String> args;
    private FileCollection classes;
    private FindBugsReports reports;

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
        for (File classFile : classes.getFiles()) {
            args.add(classFile.getAbsolutePath());
        }
        FindBugsSpec spec = new FindBugsSpec(args, debugEnabled);
        return spec;
    }

    private boolean has(FileCollection fileCollection) {
        return fileCollection != null && !fileCollection.isEmpty();
    }
}
