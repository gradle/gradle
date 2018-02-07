/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting.internal;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.Report;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.util.concurrent.Callable;

public class SimpleReport implements ConfigurableReport {

    private String name;
    private Factory<String> displayName;
    private FileResolver fileResolver;

    private final Property<File> destination;
    private final Property<Boolean> enabled;
    private final Project project;
    private OutputType outputType;

    public SimpleReport(String name, String displayName, OutputType outputType, FileResolver fileResolver, Project project) {
        this(name, Factories.constant(displayName), outputType, fileResolver, project);
    }

    public SimpleReport(String name, Factory<String> displayName, OutputType outputType, FileResolver fileResolver, Project project) {
        this.name = name;
        this.displayName = displayName;
        this.fileResolver = fileResolver;
        this.outputType = outputType;
        destination = project.getObjects().property(File.class);
        enabled = project.getObjects().property(Boolean.class);
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName.create();
    }

    public String toString() {
        return "Report " + getName();
    }

    public File getDestination() {
        return destination.getOrNull();
    }

    public void setDestination(final Object destination) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("ConfigurableReport.setDestination(Object)", String.format("Please use the method ConfigurableReport.setDestination(File) instead."));
        this.destination.set(project.provider(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return resolveToFile(destination);
            }
        }));
    }

    @Override
    public void setDestination(File file) {
        this.destination.set(file);
    }

    @Override
    public void setDestination(Provider<File> provider) {
        this.destination.set(provider);
    }

    public OutputType getOutputType() {
        return outputType;
    }

    private File resolveToFile(Object file) {
        return fileResolver.resolve(file);
    }

    public Report configure(Closure configure) {
        return ConfigureUtil.configureSelf(configure, this);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public void setEnabled(Provider<Boolean> enabled) {
        this.enabled.set(enabled);
    }
}
