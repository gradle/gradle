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
package org.gradle.nativecode.language.base.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.nativecode.base.NativeDependencySet;
import org.gradle.nativecode.base.internal.ConfigurationBasedNativeDependencySet;
import org.gradle.nativecode.base.internal.ResolvableNativeDependencySet;

import java.util.Collection;
import java.util.Map;

/**
 * A convenience base class for implementing language source sets.
 * Note that this class 'implements' all required methods for {@link org.gradle.nativecode.base.HeaderExportingSourceSet}
 * and {@link org.gradle.nativecode.base.DependentSourceSet} but does not add those types to the API.
 */
public abstract class AbstractBaseSourceSet implements LanguageSourceSetInternal {

    private final String name;
    private final String fullName;
    private final String typeName;

    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet source;
    private final ResolvableNativeDependencySet libs;
    private final ConfigurationBasedNativeDependencySet configurationDependencySet;

    public AbstractBaseSourceSet(String name, FunctionalSourceSet parent, ProjectInternal project, String typeName) {
        this.name = name;
        this.fullName = parent.getName() + StringUtils.capitalize(name);
        this.typeName = typeName;

        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", project.getFileResolver());
        this.source = new DefaultSourceDirectorySet("source", project.getFileResolver());
        this.libs = new ResolvableNativeDependencySet();
        this.configurationDependencySet = new ConfigurationBasedNativeDependencySet(project, fullName);
        
        libs.add(configurationDependencySet);
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return String.format("%s source '%s'", typeName, getFullName());
    }

    public TaskDependency getBuildDependencies() {
        // TODO -
        return new DefaultTaskDependency();
    }

    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    public void exportedHeaders(Action<SourceDirectorySet> config) {
        config.execute(getExportedHeaders());
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public void source(Action<SourceDirectorySet> config) {
        config.execute(getSource());
    }

    public Collection<NativeDependencySet> getLibs() {
        return libs.resolve();
    }

    public void lib(Object library) {
        libs.add(library);
    }

    public void dependency(Map<?, ?> dep) {
        configurationDependencySet.add(dep);
    }
}