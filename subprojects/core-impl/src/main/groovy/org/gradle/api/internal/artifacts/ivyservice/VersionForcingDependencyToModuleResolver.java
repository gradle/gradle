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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

public class VersionForcingDependencyToModuleResolver implements DependencyToModuleVersionIdResolver {
    private final DependencyToModuleVersionIdResolver resolver;
    private Action<DependencyResolveDetailsInternal> rule;

    public VersionForcingDependencyToModuleResolver(DependencyToModuleVersionIdResolver resolver, Action<DependencyResolveDetailsInternal> rule) {
        this.resolver = resolver;
        this.rule = rule;
    }

    public ModuleVersionIdResolveResult resolve(DependencyMetaData dependency) {
        ModuleVersionSelector module = dependency.getRequested();
        DefaultDependencyResolveDetails details = new DefaultDependencyResolveDetails(module);
        try {
            rule.execute(details);
        } catch (Throwable e) {
            return new FailedDependencyResolveRuleResult(module, e);
        }
        if (details.isUpdated()) {
            DependencyMetaData substitutedDependency = dependency.withRequestedVersion(details.getTarget());
            ModuleVersionIdResolveResult result = resolver.resolve(substitutedDependency);
            return new SubstitutedModuleVersionIdResolveResult(result, details.getSelectionReason());
        }
        return resolver.resolve(dependency);
    }

    private class FailedDependencyResolveRuleResult implements ModuleVersionIdResolveResult {

        private final ModuleVersionResolveException failure;

        public FailedDependencyResolveRuleResult(ModuleVersionSelector module, Throwable problem) {
            this.failure = new ModuleVersionResolveException(module, problem);
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
            return VersionSelectionReasons.REQUESTED;
        }
    }
}