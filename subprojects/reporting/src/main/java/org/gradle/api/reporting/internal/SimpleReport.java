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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.reporting.Report;
import org.gradle.util.ConfigureUtil;

import java.io.File;

public class SimpleReport implements Report {

    private String name;
    private String displayName;
    private FileResolver fileResolver;

    private Object destination;
    private boolean enabled;
    private OutputType outputType;

    public SimpleReport(String name, String displayName, OutputType outputType, FileResolver fileResolver) {
        this.name = name;
        this.displayName = displayName;
        this.fileResolver = fileResolver;
        this.outputType = outputType;
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
        return destination == null ? null : resolveToFile(destination);
    }

    protected void setDestination(Object destination) {
        this.destination = destination;
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
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
