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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.version.VersionMatcher;

/**
 * A {@link DependencyToModuleResolver} implementation which uses an Ivy {@link DependencyResolver} to resolve a dependency descriptor.
 */
public class IvyResolverBackedDependencyToModuleResolver implements DependencyToModuleResolver {
    private final Ivy ivy;
    private final ResolveData resolveData;
    private final DependencyResolver resolver;
    private final VersionMatcher versionMatcher;

    public IvyResolverBackedDependencyToModuleResolver(Ivy ivy, ResolveData resolveData, DependencyResolver resolver, VersionMatcher versionMatcher) {
        this.ivy = ivy;
        this.resolveData = resolveData;
        this.resolver = resolver;
        this.versionMatcher = versionMatcher;
    }

    public ModuleVersionResolver create(DependencyDescriptor dependencyDescriptor) {
        if (versionMatcher.isDynamic(dependencyDescriptor.getDependencyRevisionId())) {
            return new DynamicModuleVersionResolver(dependencyDescriptor);
        }
        return new DefaultModuleVersionResolver(dependencyDescriptor);
    }

    private class DefaultModuleVersionResolver implements ModuleVersionResolver {
        private final DependencyDescriptor dependencyDescriptor;
        private ModuleDescriptor moduleDescriptor;
        ModuleResolveException failure;

        public DefaultModuleVersionResolver(DependencyDescriptor dependencyDescriptor) {
            this.dependencyDescriptor = dependencyDescriptor;
        }

        public ModuleRevisionId getId() throws ModuleResolveException {
            return dependencyDescriptor.getDependencyRevisionId();
        }

        public ModuleDescriptor getDescriptor() throws ModuleResolveException {
            if (failure != null) {
                throw failure;
            }

            if (moduleDescriptor == null) {
                IvyContext context = IvyContext.pushNewCopyContext();
                try {
                    context.setIvy(ivy);
                    context.setResolveData(resolveData);
                    context.setDependencyDescriptor(dependencyDescriptor);
                    ResolvedModuleRevision resolvedRevision;
                    try {
                        resolvedRevision = resolver.getDependency(dependencyDescriptor, resolveData);
                    } catch (Throwable t) {
                        throw new ModuleResolveException(String.format("Could not resolve %s", dependencyDescriptor.getDependencyRevisionId()), t);
                    }
                    if (resolvedRevision == null) {
                        throw new ModuleNotFoundException(String.format("%s not found.", dependencyDescriptor.getDependencyRevisionId()));
                    }
                    checkDescriptor(resolvedRevision.getDescriptor());
                    moduleDescriptor = resolvedRevision.getDescriptor();
                } catch (ModuleResolveException e) {
                    failure = e;
                    throw failure;
                } finally {
                    IvyContext.popContext();
                }
            }
            return moduleDescriptor;
        }

        private void checkDescriptor(ModuleDescriptor descriptor) {
            if (!copy(descriptor.getModuleRevisionId()).equals(copy(dependencyDescriptor.getDependencyRevisionId()))) {
                onUnexpectedModuleRevisionId(descriptor);
            }
        }

        private ModuleRevisionId copy(ModuleRevisionId id) {
            // Copy to get rid of extra attributes
            return new ModuleRevisionId(new ModuleId(id.getOrganisation(), id.getName()), id.getRevision());
        }

        protected void onUnexpectedModuleRevisionId(ModuleDescriptor descriptor) {
            throw new ModuleResolveException(String.format("Received unexpected module descriptor %s for dependency %s.", descriptor.getModuleRevisionId(), dependencyDescriptor.getDependencyRevisionId()));
        }
    }

    private class DynamicModuleVersionResolver extends DefaultModuleVersionResolver {
        public DynamicModuleVersionResolver(DependencyDescriptor dependencyDescriptor) {
            super(dependencyDescriptor);
        }

        @Override
        public ModuleRevisionId getId() throws ModuleResolveException {
            return getDescriptor().getModuleRevisionId();
        }

        @Override
        protected void onUnexpectedModuleRevisionId(ModuleDescriptor descriptor) {
            // Don't care
        }
    }
}
