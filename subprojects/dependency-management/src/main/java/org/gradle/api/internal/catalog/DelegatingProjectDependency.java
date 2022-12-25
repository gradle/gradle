/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DelegatingProjectDependency implements ProjectDependencyInternal {
    private final TypeSafeProjectDependencyFactory factory;
    private final ProjectDependencyInternal delegate;

    public DelegatingProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        this.factory = factory;
        this.delegate = delegate;
    }

    protected ProjectDependencyInternal create(String path) {
        return factory.create(path);
    }

    protected TypeSafeProjectDependencyFactory getFactory() {
        return factory;
    }

    @Override
    public Configuration findProjectConfiguration() {
        return delegate.findProjectConfiguration();
    }

    @Override
    public Project getDependencyProject() {
        return delegate.getDependencyProject();
    }

    @Override
    public ProjectDependency copy() {
        return delegate.copy();
    }

    @Override
    public ModuleDependency exclude(Map<String, String> excludeProperties) {
        return delegate.exclude(excludeProperties);
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        return delegate.getExcludeRules();
    }

    @Override
    public Set<DependencyArtifact> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public ModuleDependency addArtifact(DependencyArtifact artifact) {
        return delegate.addArtifact(artifact);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public DependencyArtifact artifact(Closure configureClosure) {
        return delegate.artifact(configureClosure);
    }

    @Override
    public DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
        return delegate.artifact(configureAction);
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public ModuleDependency setTransitive(boolean transitive) {
        return delegate.setTransitive(transitive);
    }

    @Override
    @Nullable
    public String getTargetConfiguration() {
        return delegate.getTargetConfiguration();
    }

    @Override
    public void setTargetConfiguration(@Nullable String name) {
        delegate.setTargetConfiguration(name);
    }

    @Override
    public AttributeContainer getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
        return delegate.attributes(configureAction);
    }

    @Override
    public ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
        return delegate.capabilities(configureAction);
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        return delegate.getRequestedCapabilities();
    }

    @Override
    public void endorseStrictVersions() {
        delegate.endorseStrictVersions();
    }

    @Override
    public void doNotEndorseStrictVersions() {
        delegate.doNotEndorseStrictVersions();
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return delegate.isEndorsingStrictVersions();
    }

    @Override
    @Nullable
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    @Nullable
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        return delegate.contentEquals(dependency);
    }

    @Override
    @Nullable
    public String getReason() {
        return delegate.getReason();
    }

    @Override
    public void because(@Nullable String reason) {
        delegate.because(reason);
    }

    @Override
    public Set<File> resolve() {
        return delegate.resolve();
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        return delegate.resolve(transitive);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return delegate.getBuildDependencies();
    }
}
