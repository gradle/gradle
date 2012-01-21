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
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionIdResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException;
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException;

import java.io.File;

/**
 * A {@link org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver} implementation which returns lazy resolvers that don't
 * actually retrieve module descriptors until required.
 */
public class LazyDependencyToModuleResolver implements DependencyToModuleResolver {
    private final DependencyToModuleResolver dependencyResolver;
    private final VersionMatcher versionMatcher;

    public LazyDependencyToModuleResolver(DependencyToModuleResolver dependencyResolver, VersionMatcher versionMatcher) {
        this.dependencyResolver = dependencyResolver;
        this.versionMatcher = versionMatcher;
    }

    public ModuleVersionIdResolveResult resolve(DependencyDescriptor dependencyDescriptor) {
        if (versionMatcher.isDynamic(dependencyDescriptor.getDependencyRevisionId())) {
            return new DynamicModuleVersionResolver(dependencyDescriptor);
        }
        return new DefaultModuleVersionResolver(dependencyDescriptor);
    }

    private class DefaultModuleVersionResolver implements ModuleVersionIdResolveResult {
        private final DependencyDescriptor dependencyDescriptor;
        private ModuleDescriptor moduleDescriptor;
        ModuleVersionResolveException failure;
        ModuleVersionIdResolveResult resolver;

        public DefaultModuleVersionResolver(DependencyDescriptor dependencyDescriptor) {
            this.dependencyDescriptor = dependencyDescriptor;
        }

        public ModuleRevisionId getId() throws ModuleVersionResolveException {
            return dependencyDescriptor.getDependencyRevisionId();
        }

        public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
            maybeResolve();
            return moduleDescriptor;
        }

        public File getArtifact(Artifact artifact) throws ArtifactResolveException {
            maybeResolve();
            File file;
            try {
                file = resolver.getArtifact(artifact);
            } catch (Throwable t) {
                ModuleRevisionId id = artifact.getModuleRevisionId();
                throw new ArtifactResolveException(String.format("Could not resolve artifact group:%s, module:%s, version:%s, name:%s.", id.getOrganisation(), id.getName(), id.getRevision(), artifact.getName()), t);
            }
            if (file == null) {
                ModuleRevisionId id = artifact.getModuleRevisionId();
                throw new ArtifactNotFoundException(String.format("Artifact group:%s, module:%s, version:%s, name:%s not found.", id.getOrganisation(), id.getName(), id.getRevision(), artifact.getName()));
            }
            return file;
        }

        private void maybeResolve() {
            if (failure != null) {
                throw failure;
            }

            if (moduleDescriptor == null) {
                try {
                    try {
                        resolver = dependencyResolver.resolve(dependencyDescriptor);
                    } catch (Throwable t) {
                        ModuleRevisionId id = dependencyDescriptor.getDependencyRevisionId();
                        throw new ModuleVersionResolveException(String.format("Could not resolve group:%s, module:%s, version:%s.", id.getOrganisation(), id.getName(), id.getRevision()), t);
                    }
                    if (resolver == null) {
                        ModuleRevisionId id = dependencyDescriptor.getDependencyRevisionId();
                        throw notFound(id);
                    }
                    checkDescriptor(resolver.getDescriptor());
                    moduleDescriptor = resolver.getDescriptor();
                } catch (ModuleVersionResolveException e) {
                    failure = e;
                    throw failure;
                }
            }
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
            return new ModuleVersionNotFoundException(String.format("Could not find group:%s, module:%s, version:%s.", id.getOrganisation(), id.getName(), id.getRevision()));
        }

        protected void onUnexpectedModuleRevisionId(ModuleDescriptor descriptor) {
            throw new ModuleVersionResolveException(String.format("Received unexpected module descriptor %s for dependency %s.", descriptor.getModuleRevisionId(), dependencyDescriptor.getDependencyRevisionId()));
        }
    }

    private class DynamicModuleVersionResolver extends DefaultModuleVersionResolver {
        public DynamicModuleVersionResolver(DependencyDescriptor dependencyDescriptor) {
            super(dependencyDescriptor);
        }

        @Override
        public ModuleRevisionId getId() throws ModuleVersionResolveException {
            return getDescriptor().getModuleRevisionId();
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
