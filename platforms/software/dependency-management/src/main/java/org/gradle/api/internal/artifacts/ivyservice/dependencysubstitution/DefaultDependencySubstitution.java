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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.typeconversion.NotationParser;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.artifacts.result.ComponentSelectionCause.SELECTED_BY_RULE;

public class DefaultDependencySubstitution implements DependencySubstitutionInternal {

    private static final NotationParser<Object, ComponentSelector> COMPONENT_SELECTOR_PARSER = ComponentSelectorParsers.parser();

    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final ComponentSelector requestedSelector;
    private final ImmutableList<IvyArtifactName> requestedArtifacts;

    private @Nullable ComponentSelector target;
    private @Nullable List<ComponentSelectionDescriptorInternal> ruleDescriptors;
    private @Nullable ArtifactSelectionDetailsInternal artifactSelectionDetails;

    @Inject
    public DefaultDependencySubstitution(
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ComponentSelector requestedSelector,
        ImmutableList<IvyArtifactName> requestedArtifacts
    ) {
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.requestedSelector = requestedSelector;
        this.requestedArtifacts = requestedArtifacts;
    }

    @Override
    public ComponentSelector getRequested() {
        return requestedSelector;
    }

    @Override
    public void useTarget(Object notation) {
        useTarget(notation, ComponentSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public void useTarget(Object notation, String reason) {
        useTarget(notation, componentSelectionDescriptorFactory.newDescriptor(SELECTED_BY_RULE, reason));
    }

    @Override
    public void artifactSelection(Action<? super ArtifactSelectionDetails> action) {
        if (artifactSelectionDetails == null) {
            artifactSelectionDetails = new DefaultArtifactSelectionDetails(requestedArtifacts);
        }
        action.execute(artifactSelectionDetails);
    }

    @Override
    public void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor) {
        this.target = COMPONENT_SELECTOR_PARSER.parseNotation(notation);
        if (this.ruleDescriptors == null) {
            this.ruleDescriptors = new ArrayList<>();
        }
        this.ruleDescriptors.add((ComponentSelectionDescriptorInternal) ruleDescriptor);
        validateTarget(target);
    }

    @Override
    public @Nullable ImmutableList<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
        boolean hasConfiguredTarget = getConfiguredTargetSelector() != null;
        boolean hasConfiguredArtifactSelectors = getConfiguredArtifactSelectors() != null;

        if (!hasConfiguredTarget && !hasConfiguredArtifactSelectors) {
            return null;
        }

        ImmutableList.Builder<ComponentSelectionDescriptorInternal> builder = ImmutableList.builder();
        if (hasConfiguredTarget) {
            assert ruleDescriptors != null;
            builder.addAll(ruleDescriptors);
        }

        if (hasConfiguredArtifactSelectors) {
            builder.add(ComponentSelectionReasons.SELECTED_BY_RULE);
        }

        return builder.build();
    }

    @Override
    public @Nullable ComponentSelector getConfiguredTargetSelector() {
        return target;
    }

    @Override
    public @Nullable ImmutableList<DependencyArtifactSelector> getConfiguredArtifactSelectors() {
        return artifactSelectionDetails != null ? artifactSelectionDetails.getConfiguredSelectors() : null;
    }

    public static void validateTarget(ComponentSelector componentSelector) {
        if (componentSelector instanceof UnversionedModuleComponentSelector) {
            throw new InvalidUserDataException("Must specify version for target of dependency substitution");
        }
    }

}
