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
package org.gradle.language.internal;

import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A convenience base class for implementing language source sets.
 * Note that this class 'implements' all required methods for {@link org.gradle.language.HeaderExportingSourceSet}
 * and {@link org.gradle.language.DependentSourceSet} but does not add those types to the API.
 */
public abstract class AbstractBaseSourceSet extends AbstractLanguageSourceSet {
    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet source;
    private final List<Object> libs = new ArrayList<Object>();
    private final ConfigurationBasedNativeDependencySet configurationDependencySet;

    public AbstractBaseSourceSet(String name, FunctionalSourceSet parent, ProjectInternal project, String typeName) {
        super(name, parent, typeName);

        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", project.getFileResolver());
        this.source = new DefaultSourceDirectorySet("source", project.getFileResolver());
        this.configurationDependencySet = new ConfigurationBasedNativeDependencySet(project, getFullName());

        libs.add(configurationDependencySet);
    }

    public TaskDependency getBuildDependencies() {
        // TODO -
        return source.getBuildDependencies();
    }

    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    public void exportedHeaders(Action<? super SourceDirectorySet> config) {
        config.execute(getExportedHeaders());
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public Collection<?> getLibs() {
        return libs;
    }

    public void lib(Object library) {
        libs.add(library);
    }

    public void dependency(Map<?, ?> dep) {
        configurationDependencySet.add(dep);
    }
}