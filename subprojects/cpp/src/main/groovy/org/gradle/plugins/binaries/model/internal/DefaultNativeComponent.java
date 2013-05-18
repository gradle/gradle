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
package org.gradle.plugins.binaries.model.internal;

import groovy.lang.Closure;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.CompileSpec;
import org.gradle.plugins.binaries.model.NativeComponent;
import org.gradle.plugins.binaries.model.SourceSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.io.File;

public class DefaultNativeComponent implements NativeComponent {
    private final String name;
    private final BinaryCompileSpec spec;
    private final DomainObjectSet<SourceSet> sourceSets;
    private final Project project;
    private String baseName;

    public DefaultNativeComponent(String name, CompileSpecFactory specFactory, Project project) {
        this.name = name;
        this.sourceSets = new DefaultDomainObjectSet<SourceSet>(SourceSet.class);
        this.spec = specFactory.create(this);
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public TaskDependency getBuildDependencies() {
        return spec.getBuildDependencies();
    }

    public CompileSpec getSpec() {
        return spec;
    }

    public DomainObjectSet<SourceSet> getSourceSets() {
        return sourceSets;
    }

    public String getBaseName() {
        return GUtil.elvis(baseName, name);
    }

    public String getOutputFileName() {
        return OperatingSystem.current().getExecutableName(getBaseName());
    }

    public File getOutputFile() {
        return new File(project.getBuildDir(), "binaries/" + getOutputFileName());
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public CompileSpec spec(Closure closure) {
        return ConfigureUtil.configure(closure, spec);
    }
}