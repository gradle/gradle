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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

/**
 * A {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionIdResolver} implementation which returns lazy resolvers that don't actually retrieve module descriptors until
 * required.
 */
public class LazyDependencyToModuleResolver implements DependencyToModuleVersionIdResolver {
    private final DependencyToModuleResolver dependencyResolver;
    private final VersionMatcher versionMatcher;

    public LazyDependencyToModuleResolver(DependencyToModuleResolver dependencyResolver, VersionMatcher versionMatcher) {
        this.dependencyResolver = dependencyResolver;
        this.versionMatcher = versionMatcher;
    }

    public ModuleVersionIdResolveResult resolve(DependencyDescriptor dependencyDescriptor) {
        if (versionMatcher.isDynamic(dependencyDescriptor.getDependencyRevisionId())) {
            DynamicVersionResolveResult result = new DynamicVersionResolveResult(dependencyDescriptor);
            result.resolve();
            return result;
        }
        return new StaticVersionResolveResult(dependencyDescriptor);
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

    private class StaticVersionResolveResult implements ModuleVersionIdResolveResult {
        private final DependencyDescriptor dependencyDescriptor;
        private BuildableModuleVersionResolveResult resolveResult;

        public StaticVersionResolveResult(DependencyDescriptor dependencyDescriptor) {
            this.dependencyDescriptor = dependencyDescriptor;
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            final ModuleRevisionId dependencyRevisionId = dependencyDescriptor.getDependencyRevisionId();
            return new DefaultModuleVersionIdentifier(dependencyRevisionId.getOrganisation(), dependencyRevisionId.getName(), dependencyRevisionId.getRevision());
        }

        public ModuleVersionResolveException getFailure() {
            return null;
        }

        public ModuleVersionResolveResult resolve() {
            if (resolveResult == null) {
                resolveResult = new DefaultBuildableModuleVersionResolveResult();
                try {
                    try {
                        dependencyResolver.resolve(dependencyDescriptor, resolveResult);
                    } catch (Throwable t) {
                        throw new ModuleVersionResolveException(dependencyDescriptor.getDependencyRevisionId(), t);
                    }
                    if (resolveResult.getFailure() instanceof ModuleVersionNotFoundException) {
                        throw notFound(dependencyDescriptor.getDependencyRevisionId());
                    }
                    if (resolveResult.getFailure() != null) {
                        throw resolveResult.getFailure();
                    }
                    checkDescriptor(resolveResult.getDescriptor());
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

        private void checkDescriptor(ModuleDescriptor descriptor) {
            ModuleRevisionId id = descriptor.getModuleRevisionId();
            if (!copy(id).equals(copy(dependencyDescriptor.getDependencyRevisionId()))) {
                onUnexpectedModuleRevisionId(descriptor);
            }
            for (Configuration configuration : descriptor.getConfigurations()) {
                for (String parent : configuration.getExtends()) {
                    if (descriptor.getConfiguration(parent) == null) {
                        throw new ModuleVersionResolveException(String.format("Configuration '%s' extends unknown configuration '%s' in module descriptor for group:%s, module:%s, version:%s.",
                                configuration.getName(), parent, id.getOrganisation(), id.getName(), id.getRevision()));
                    }
                }
            }
        }

        private ModuleRevisionId copy(ModuleRevisionId id) {
            // Copy to get rid of extra attributes
            return new ModuleRevisionId(new ModuleId(id.getOrganisation(), id.getName()), id.getRevision());
        }

        protected ModuleVersionNotFoundException notFound(ModuleRevisionId id) {
            return new ModuleVersionNotFoundException(id);
        }

        protected void onUnexpectedModuleRevisionId(ModuleDescriptor descriptor) {
            throw new ModuleVersionResolveException(String.format("Received unexpected module descriptor %s for dependency %s.", descriptor.getModuleRevisionId(), dependencyDescriptor.getDependencyRevisionId()));
        }
    }

    private class DynamicVersionResolveResult extends StaticVersionResolveResult {
        public DynamicVersionResolveResult(DependencyDescriptor dependencyDescriptor) {
            super(dependencyDescriptor);
        }

        @Override
        public ModuleVersionResolveException getFailure() {
            return resolve().getFailure();
        }

        @Override
        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            return resolve().getId();
        }

        @Override
        protected ModuleVersionNotFoundException notFound(ModuleRevisionId id) {
            return new ModuleVersionNotFoundException(String.format("Could not find any version that matches group:%s, module:%s, version:%s.", id.getOrganisation(), id.getName(), id.getRevision()));
        }

        @Override
        protected void onUnexpectedModuleRevisionId(ModuleDescriptor descriptor) {
            // Don't care
        }
    }
}
