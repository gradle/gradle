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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.artifacts.result.ComponentSelectionCause.SELECTED_BY_RULE;

public class DefaultDependencySubstitution implements DependencySubstitutionInternal {
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final ComponentSelector requested;
    private List<ComponentSelectionDescriptorInternal> ruleDescriptors;
    private ComponentSelector target;
    private final ArtifactSelectionDetailsInternal artifactSelectionDetails;

    @Inject
    public DefaultDependencySubstitution(ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
                                         ComponentSelector requested,
                                         List<IvyArtifactName> artifacts) {
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.requested = requested;
        this.target = requested;
        this.artifactSelectionDetails = new DefaultArtifactSelectionDetails(this, artifacts);
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
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
        action.execute(artifactSelectionDetails);
    }

    @Override
    public void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor) {
        this.target = ComponentSelectorParsers.parser().parseNotation(notation);
        addRuleDescriptor((ComponentSelectionDescriptorInternal) ruleDescriptor);
        validateTarget(target);
    }

    void addRuleDescriptor(ComponentSelectionDescriptorInternal ruleDescriptor) {
        if (this.ruleDescriptors == null) {
            this.ruleDescriptors = Lists.newArrayList();
        }
        this.ruleDescriptors.add(ruleDescriptor);
    }

    @Override
    public List<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
        return ruleDescriptors == null ? Collections.emptyList() : ruleDescriptors;
    }

    @Override
    public ComponentSelector getTarget() {
        return target;
    }

    @Override
    public boolean isUpdated() {
        return ruleDescriptors != null;
    }

    @Override
    public ArtifactSelectionDetailsInternal getArtifactSelectionDetails() {
        return artifactSelectionDetails;
    }

    public static void validateTarget(ComponentSelector componentSelector) {
        if (componentSelector instanceof UnversionedModuleComponentSelector) {
            throw new InvalidUserDataException("Must specify version for target of dependency substitution");
        }
    }
}
