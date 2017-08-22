/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.composite.internal.IncludedBuildFactory;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlSystem;

import java.io.File;
import java.util.Collections;

public class ExternalSourceDependencyResolver implements DependencyToComponentIdResolver, ComponentResolvers {
    private final ProjectDependencyResolver resolver;
    private final Action<VcsMapping> rule;
    private final VersionControlSystemFactory versionControlSystemFactory;
    private final VcsMappingFactory vcsMappingFactory;
    private final ServiceRegistry serviceRegistry;

    public ExternalSourceDependencyResolver(ProjectDependencyResolver resolver, Action<VcsMapping> rule, VersionControlSystemFactory versionControlSystemFactory, VcsMappingFactory vcsMappingFactory, ServiceRegistry serviceRegistry) {
        this.resolver = resolver;
        this.rule = rule;
        this.versionControlSystemFactory = versionControlSystemFactory;
        this.vcsMappingFactory = vcsMappingFactory;
        this.serviceRegistry = serviceRegistry;
    }

    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        ComponentSelector selector = dependency.getSelector();
        VcsMappingInternal details = vcsMappingFactory.create(selector, dependency.getRequested());
        try {
            rule.execute(details);
        } catch (Throwable e) {
            result.failed(new ModuleVersionResolveException(selector, e));
            return;
        }
        if (details.isUpdated()) {
            // TODO: Adding checked out build as an included build... this should only happen once
            VersionControlSystem versionControlSystem = versionControlSystemFactory.create(details.getRepository());
            File workingDir = new File("working-dir");
            versionControlSystem.populate(workingDir, details.getRepository());
            IncludedBuild includedBuild = getIncludedBuildFactory().createBuild(workingDir);
            getCompositeContextBuilder().addIncludedBuilds(Collections.singleton(includedBuild));
            // TODO: Hardcoded
            resolver.resolve(dependency.withTarget(DefaultProjectComponentSelector.newSelector(includedBuild, ":")), result);
            result.setSelectionReason(VersionSelectionReasons.COMPOSITE_BUILD);
            return;
        }
        resolver.resolve(dependency, result);
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return resolver;
    }

    @Override
    public OriginArtifactSelector getArtifactSelector() {
        return resolver;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return resolver;
    }

    private CompositeContextBuilder getCompositeContextBuilder() {
        return serviceRegistry.get(CompositeContextBuilder.class);
    }
    private IncludedBuildFactory getIncludedBuildFactory() {
        return serviceRegistry.get(IncludedBuildFactory.class);
    }
}
