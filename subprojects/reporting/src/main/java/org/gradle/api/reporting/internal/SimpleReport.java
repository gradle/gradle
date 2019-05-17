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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.Report;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;

import java.io.File;

public class SimpleReport implements ConfigurableReport {

    private String name;
    private Factory<String> displayName;

    private final Property<File> destination;
    private final Property<Boolean> enabled;
    private OutputType outputType;

    public SimpleReport(String name, Factory<String> displayName, OutputType outputType, Project project) {
        this.name = name;
        this.displayName = displayName;
        this.outputType = outputType;
        destination = project.getObjects().property(File.class);
        enabled = project.getObjects().property(Boolean.class).value(false);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName.create();
    }

    public String toString() {
        return "Report " + getName();
    }

    @Override
    public File getDestination() {
        return destination.getOrNull();
    }

    @Override
    public void setDestination(File file) {
        this.destination.set(file);
    }

    @Override
    public void setDestination(Provider<File> provider) {
        this.destination.set(provider);
    }

    @Override
    public OutputType getOutputType() {
        return outputType;
    }

    @Override
    public Report configure(Closure configure) {
        return ConfigureUtil.configureSelf(configure, this);
    }

    @Override
    public boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public void setEnabled(Provider<Boolean> enabled) {
        this.enabled.set(enabled);
    }
}
