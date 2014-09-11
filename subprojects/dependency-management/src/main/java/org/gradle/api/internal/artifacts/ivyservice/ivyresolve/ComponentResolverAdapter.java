/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToModuleVersionIdResolver;
import org.gradle.internal.resolve.result.*;

/**
 * Takes separate dependency->id and id->meta-data resolvers and presents them as a single dependency->meta-data resolver.
 */
public class ComponentResolverAdapter implements DependencyToModuleVersionIdResolver {
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;

    public ComponentResolverAdapter(DependencyToComponentIdResolver idResolver, ComponentMetaDataResolver metaDataResolver) {
        this.idResolver = idResolver;
        this.metaDataResolver = metaDataResolver;
    }

    public ModuleVersionIdResolveResult resolve(final DependencyMetaData dependency) {
        final DefaultBuildableComponentIdResolveResult idResult = new DefaultBuildableComponentIdResolveResult();
        idResolver.resolve(dependency, idResult);
        if (idResult.getFailure() != null) {
            return new BrokenResult(idResult);
        }
        if (idResult.getMetaData() != null) {
            return new WithMetaDataResult(idResult);
        }
        return new LazyResult(idResult, dependency);
    }

    private static class WithMetaDataResult implements ModuleVersionIdResolveResult {
        private final BuildableComponentIdResolveResult idResult;

        public WithMetaDataResult(BuildableComponentIdResolveResult idResult) {
            this.idResult = idResult;
        }

        public ModuleVersionResolveException getFailure() {
            return null;
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            return idResult.getModuleVersionId();
        }

        public ComponentResolveResult resolve() throws ModuleVersionResolveException {
            DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
            result.resolved(idResult.getMetaData());
            return result;
        }

        public ComponentSelectionReason getSelectionReason() {
            return idResult.getSelectionReason();
        }

        public boolean hasResult() {
            return true;
        }
    }

    private static class BrokenResult implements ModuleVersionIdResolveResult {
        private final ModuleVersionResolveException failure;
        private final BuildableComponentIdResolveResult idResult;

        public BrokenResult(BuildableComponentIdResolveResult idResult) {
            failure = idResult.getFailure();
            this.idResult = idResult;
        }

        public ModuleVersionResolveException getFailure() {
            return failure;
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            throw failure;
        }

        public ComponentResolveResult resolve() throws ModuleVersionResolveException {
            throw failure;
        }

        public ComponentSelectionReason getSelectionReason() {
            return idResult.getSelectionReason();
        }

        public boolean hasResult() {
            return true;
        }
    }

    private class LazyResult implements ModuleVersionIdResolveResult {
        private final BuildableComponentIdResolveResult idResult;
        private final DependencyMetaData dependency;

        public LazyResult(BuildableComponentIdResolveResult idResult, DependencyMetaData dependency) {
            this.idResult = idResult;
            this.dependency = dependency;
        }

        public ModuleVersionResolveException getFailure() {
            return null;
        }

        public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
            return idResult.getModuleVersionId();
        }

        public ComponentResolveResult resolve() throws ModuleVersionResolveException {
            DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
            metaDataResolver.resolve(dependency, idResult.getId(), result);
            return result;
        }

        public ComponentSelectionReason getSelectionReason() {
            return idResult.getSelectionReason();
        }

        public boolean hasResult() {
            return true;
        }
    }
}
