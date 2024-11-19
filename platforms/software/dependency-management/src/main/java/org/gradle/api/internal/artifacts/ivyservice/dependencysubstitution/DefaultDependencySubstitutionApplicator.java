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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;

public class DefaultDependencySubstitutionApplicator implements DependencySubstitutionApplicator {
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final Action<? super DependencySubstitutionInternal> rule;
    private final InstanceFactory<DefaultDependencySubstitution> substitutionFactory;

    public DefaultDependencySubstitutionApplicator(
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        Action<? super DependencySubstitutionInternal> rule,
        InstantiatorFactory instantiatorFactory
    ) {
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.rule = rule;
        this.substitutionFactory = instantiatorFactory.decorateScheme().forType(DefaultDependencySubstitution.class);
    }

    @Override
    public SubstitutionResult apply(DependencyMetadata dependency) {
        DependencySubstitutionInternal details = substitutionFactory.newInstance(
            componentSelectionDescriptorFactory,
            dependency.getSelector(),
            dependency.getArtifacts()
        );
        try {
            rule.execute(details);
        } catch (Exception e) {
            return SubstitutionResult.failed(e);
        }
        return SubstitutionResult.of(details);
    }

}
