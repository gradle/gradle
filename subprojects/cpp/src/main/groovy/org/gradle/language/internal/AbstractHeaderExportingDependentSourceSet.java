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
import org.gradle.language.DependentSourceSet;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
public abstract class AbstractHeaderExportingDependentSourceSet extends AbstractLanguageSourceSet
        implements HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSet {

    private final DefaultSourceDirectorySet exportedHeaders;
    private final List<Object> libs = new ArrayList<Object>();
    private final ConfigurationBasedNativeDependencySet configurationDependencySet;

    public AbstractHeaderExportingDependentSourceSet(String name, FunctionalSourceSet parent, ProjectInternal project, String typeName, SourceDirectorySet source) {
        super(name, parent, typeName, source);

        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", project.getFileResolver());
        this.configurationDependencySet = new ConfigurationBasedNativeDependencySet(project, getFullName());

        libs.add(configurationDependencySet);
    }

    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    public void exportedHeaders(Action<? super SourceDirectorySet> config) {
        config.execute(getExportedHeaders());
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