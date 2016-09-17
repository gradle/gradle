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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public class DependencySubstitutionResolver implements DependencyToComponentIdResolver {
    private final DependencyToComponentIdResolver resolver;
    private final Action<DependencySubstitution> rule;

    public DependencySubstitutionResolver(DependencyToComponentIdResolver resolver, Action<DependencySubstitution> rule) {
        this.resolver = resolver;
        this.rule = rule;
    }

    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        ComponentSelector selector = dependency.getSelector();
        DependencySubstitutionInternal details = new DefaultDependencySubstitution(selector, dependency.getRequested());
        try {
            rule.execute(details);
        } catch (Throwable e) {
            result.failed(new ModuleVersionResolveException(selector, e));
            return;
        }
        if (details.isUpdated()) {
            resolver.resolve(dependency.withTarget(details.getTarget()), result);
            result.setSelectionReason(details.getSelectionReason());
            return;
        }
        resolver.resolve(dependency, result);
    }
}
