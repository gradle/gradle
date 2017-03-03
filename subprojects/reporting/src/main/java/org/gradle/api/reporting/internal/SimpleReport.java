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
import org.gradle.api.provider.PropertyState;
import org.gradle.api.reporting.Report;
import org.gradle.util.ConfigureUtil;

import java.io.File;

public class SimpleReport implements Report {

    private String name;
    private String displayName;
    private FileResolver fileResolver;

    private PropertyState<Object> destination;
    private final PropertyState<Boolean> enabled;
    private OutputType outputType;

    public SimpleReport(String name, String displayName, OutputType outputType, FileResolver fileResolver, Project project) {
        this.name = name;
        this.displayName = displayName;
        this.fileResolver = fileResolver;
        this.outputType = outputType;
        destination = project.property(Object.class);
        enabled = project.property(Boolean.class);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String toString() {
        return "Report " + getName();
    }

    public File getDestination() {
        Object evaluatedDestination = destination.get();
        return evaluatedDestination == null ? null : resolveToFile(evaluatedDestination);
    }

    public void setDestination(Object destination) {
        this.destination.set(destination);
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
}
