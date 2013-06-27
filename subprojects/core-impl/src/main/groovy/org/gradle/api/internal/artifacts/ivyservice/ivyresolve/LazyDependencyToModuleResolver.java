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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

/**
 * A {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionIdResolver} implementation which returns lazy resolvers that don't actually retrieve module descriptors until
 * required.
 */
public class LazyDependencyToModuleResolver implements DependencyToModuleVersionIdResolver {
    private final DependencyToModuleVersionResolver dependencyResolver;
    private final VersionMatcher versionMatcher;

    public LazyDependencyToModuleResolver(DependencyToModuleVersionResolver dependencyResolver, VersionMatcher versionMatcher) {
        this.dependencyResolver = dependencyResolver;
        this.versionMatcher = versionMatcher;
    }

    public ModuleVersionIdResolveResult resolve(DependencyMetaData dependency) {
        if (versionMatcher.isDynamic(dependency.getDescriptor().getDependencyRevisionId())) {
            DynamicVersionResolveResult result = new DynamicVersionResolveResult(dependency);
            result.resolve();
            return result;
        }
        return new StaticVersionResolveResult(dependency);
    }

    private static class ErrorHandlingArtifactResolver implements ArtifactResolver {
        private final ArtifactResolver resolver;

        private ErrorHandlingArtifactResolver(ArtifactResolver resolver) {
            this.resolver = resolver;
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
            try {
                resolver.resolve(artifact, result);
            } catch (Throwable t) {
                result.failed(new ArtifactResolveException(artifact, t));
            }
        }
    }

    private abstract class AbstractVersionResolveResult implements ModuleVersionIdResolveResult {
        final DependencyMetaData dependency;
        private BuildableModuleVersionResolveResult resolveResult;

        public AbstractVersionResolveResult(DependencyMetaData dependency) {
            this.dependency = dependency;
        }

        public ModuleVersionResolveException getFailure() {
            return null;
        }

        public ModuleVersionResolveResult resolve() {
            if (resolveResult == null) {
                resolveResult = new DefaultBuildableModuleVersionResolveResult();
                try {
                    try {
                        dependencyResolver.resolve(dependency, resolveResult);
                    } catch (Throwable t) {
                        throw new ModuleVersionResolveException(dependency.getRequested(), t);
                    }
                    if (resolveResult.getFailure() instanceof ModuleVersionNotFoundException) {
                        throw notFound();
                    }
                    if (resolveResult.getFailure() != null) {
                        throw resolveResult.getFailure();
                    }
                    checkDescriptor(resolveResult.getMetaData());
                    resolveResult.setArtifactResolver(new ErrorHandlingArtifactResolver(resolveResult.getArtifactResolver()));
                } catch (ModuleVersionResolveException e) {
                    resolveResult.failed(e);
                }
            }

            return resolveResult;
        }

        public ModuleVersionSelectionReason getSelectionReason() {
            return VersionSelectionReasons.REQUESTED;
        }

        protected void checkDescriptor(ModuleVersionMetaData metaData) {
            ModuleDescriptor moduleDescriptor = metaData.getDescriptor();
            for (Configuration configuration : moduleDescriptor.getConfigurations()) {
                for (String parent : configuration.getExtends()) {
                    if (moduleDescriptor.getConfiguration(parent) == null) {
                        throw new ModuleVersionResolveException(metaData.getId(), String.format("Configuration '%s' extends unknown configuration '%s' in module descriptor for %%s.", configuration.getName(), parent));
                    }
                }
            }
        }

        protected abstract ModuleVersionNotFoundException notFound();
    }

    private class StaticVersionResolveResult extends AbstractVersionResolveResult {
        private final ModuleVersionIdentifier id;

        public StaticVersionResolveResult(DependencyMetaData dependency) {
            super(dependency);
            ModuleVersionSelector requested = dependency.getRequested();
            id = new DefaultModuleVersionIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            return id;
        }

        public ModuleVersionSelectionReason getSelectionReason() {
            return VersionSelectionReasons.REQUESTED;
        }

        @Override
        protected void checkDescriptor(ModuleVersionMetaData metaData) {
            if (!id.equals(metaData.getId())) {
                throw new ModuleVersionResolveException(dependency.getRequested(), String.format("Received unexpected module descriptor %s for dependency %%s.", metaData.getId()));
            }
            super.checkDescriptor(metaData);
        }

        protected ModuleVersionNotFoundException notFound() {
            return new ModuleVersionNotFoundException(id);
        }
    }

    private class DynamicVersionResolveResult extends AbstractVersionResolveResult {
        public DynamicVersionResolveResult(DependencyMetaData dependency) {
            super(dependency);
        }

        @Override
        public ModuleVersionResolveException getFailure() {
            return resolve().getFailure();
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            return resolve().getId();
        }

        @Override
        protected ModuleVersionNotFoundException notFound() {
            return new ModuleVersionNotFoundException(dependency.getRequested());
        }
    }
}
