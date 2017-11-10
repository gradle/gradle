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

import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public class DependencySubstitutionResolver implements DependencyToComponentIdResolver {
    private final DependencyToComponentIdResolver resolver;
    private final DependencySubstitutionApplicator applicator;

    public DependencySubstitutionResolver(DependencyToComponentIdResolver resolver, DependencySubstitutionApplicator applicator) {
        this.resolver = resolver;
        this.applicator = applicator;
    }

    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        DependencySubstitutionApplicator.SubstitutionResult application = applicator.apply(dependency);
        if (application.hasFailure()) {
            result.failed(new ModuleVersionResolveException(dependency.getSelector(), application.getFailure()));
            return;
        }
        DependencySubstitutionInternal details = application.getResult();
        if (details.isUpdated()) {
            DependencyMetadata target = dependency.withTarget(details.getTarget());
            resolver.resolve(target, result);
            result.setSelectionReason(details.getSelectionReason());
            return;
        }
        resolver.resolve(dependency, result);
    }
}
