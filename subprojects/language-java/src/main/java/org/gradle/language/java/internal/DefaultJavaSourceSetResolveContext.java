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
package org.gradle.language.java.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.ResolveContextInternal;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLibraryComponentIdentifier;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;

public class DefaultJavaSourceSetResolveContext implements ResolveContextInternal {
    private final Project project;
    private final DefaultJavaLanguageSourceSet sourceSet;

    public DefaultJavaSourceSetResolveContext(Project project, DefaultJavaLanguageSourceSet sourceSet) {
        this.project = project;
        this.sourceSet = sourceSet;
    }

    @Override
    public String getName() {
        return DefaultLibraryComponentIdentifier.libraryToConfigurationName(project.getPath(), getLibraryName());
    }

    private String getLibraryName() {
        return sourceSet.getParentName();
    }

    @Override
    public DependencySet getDependencies() {
        DefaultDomainObjectSet<Dependency> backingSet = new DefaultDomainObjectSet<Dependency>(Dependency.class);
        return new DefaultDependencySet(getLibraryName(), backingSet);
    }


    @Override
    public DependencySet getAllDependencies() {
        return new DefaultDependencySet(getLibraryName(), new DefaultDomainObjectSet<Dependency>(Dependency.class));
    }

    public DefaultJavaLanguageSourceSet getSourceSet() {
        return sourceSet;
    }

    public Project getProject() {
        return project;
    }

    @Override
    public ProjectDependencyResolver newProjectDependencyResolver(final ProjectComponentRegistry projectComponentRegistry, final LocalComponentFactory localComponentFactory, DependencyToComponentIdResolver delegateIdResolver, ComponentMetaDataResolver delegateComponentResolver) {
        return new ProjectLibraryDependencyResolver((ProjectInternal) getProject(), projectComponentRegistry, localComponentFactory, delegateIdResolver, delegateComponentResolver);
    }

}
