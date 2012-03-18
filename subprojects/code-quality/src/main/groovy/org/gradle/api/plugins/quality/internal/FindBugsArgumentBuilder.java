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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.quality.FindBugsReports;

import java.util.ArrayList;
import java.util.List;

public class FindBugsArgumentBuilder {
    private FileCollection pluginsList;
    private FileCollection sources;
    private FileCollection classpath;

    private ArrayList<String> args;
    private FileCollection classes;
    private FindBugsReports reports;

    public FindBugsArgumentBuilder(FileCollection classes) {
        if(classes == null || classes.isEmpty()){
            throw new InvalidUserDataException("Classes must be configured to be analyzed by the Findbugs.");
        }
        this.classes = classes;
    }

    public FindBugsArgumentBuilder withPluginsList(FileCollection pluginsClasspath) {
        this.pluginsList = pluginsClasspath;
        return this;
    }

    public FindBugsArgumentBuilder withSources(FileCollection sources) {
        this.sources = sources;
        return this;
    }

    public FindBugsArgumentBuilder withClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    public FindBugsArgumentBuilder configureReports(FindBugsReports reports){
        this.reports = reports;
        return this;
    }

    public List<String> build() {
        args = new ArrayList<String>();
        args.add("-pluginList");
        args.add(pluginsList==null ? "" : pluginsList.getAsPath());
        args.add("-sortByClass");
        args.add("-timestampNow");
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
            args.add(classpath.getAsPath());
        }
        args.add("-exitcode");
        args.add(classes.getAsPath());
        return args;
    }

    private boolean has(FileCollection fileCollection) {
        return fileCollection != null && !fileCollection.isEmpty();
    }
}
