/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.AbstractLanguageSourceSet;

/**
 * A convenience base class for implementing language source sets with dependencies and exported headers.
 */
public abstract class AbstractHeaderExportingSourceSet extends AbstractLanguageSourceSet
        implements HeaderExportingSourceSet, LanguageSourceSet {

    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet implicitHeaders;

    public AbstractHeaderExportingSourceSet(String name, FunctionalSourceSet parent, ProjectInternal project, String typeName, SourceDirectorySet source) {
        super(name, parent, typeName, source);
        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", project.getFileResolver());
        this.implicitHeaders = new DefaultSourceDirectorySet("implicit headers", project.getFileResolver());
    }

    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    public void exportedHeaders(Action<? super SourceDirectorySet> config) {
        config.execute(getExportedHeaders());
    }

    public SourceDirectorySet getImplicitHeaders() {
        return implicitHeaders;
    }
}