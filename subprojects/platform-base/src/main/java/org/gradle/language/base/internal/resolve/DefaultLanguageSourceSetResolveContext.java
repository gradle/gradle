/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.resolve;

import org.gradle.api.artifacts.ResolveContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLibraryComponentIdentifier;
import org.gradle.language.base.sources.BaseLanguageSourceSet;

public abstract class DefaultLanguageSourceSetResolveContext implements ResolveContext {
    private final ProjectInternal project;
    private final BaseLanguageSourceSet sourceSet;

    public DefaultLanguageSourceSetResolveContext(ProjectInternal project, BaseLanguageSourceSet sourceSet) {
        this.project = project;
        this.sourceSet = sourceSet;
    }

    protected String getLibraryName() {
        return sourceSet.getParentName();
    }

    public BaseLanguageSourceSet getSourceSet() {
        return sourceSet;
    }

    @Override
    public String getName() {
        return DefaultLibraryComponentIdentifier.libraryToConfigurationName(project.getPath(), getLibraryName());
    }

    @Override
    public ProjectInternal getProject() {
        return project;
    }
}
