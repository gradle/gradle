/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.Path;

import java.util.List;

public class DefaultProjectComponentSelector implements ProjectComponentSelectorInternal {

    private final ProjectIdentity projectIdentity;
    private final ImmutableAttributes attributes;
    private final ImmutableSet<CapabilitySelector> capabilitySelectors;

    private final int hashCode;

    public DefaultProjectComponentSelector(
        ProjectIdentity projectIdentity,
        ImmutableAttributes attributes,
        ImmutableSet<CapabilitySelector> capabilitySelectors
    ) {
        this.projectIdentity = projectIdentity;
        this.attributes = attributes;
        this.capabilitySelectors = capabilitySelectors;
        this.hashCode = computeHashCode(attributes, capabilitySelectors, projectIdentity);
    }

    private static int computeHashCode(
        ImmutableAttributes attributes,
        ImmutableSet<CapabilitySelector> capabilitySelectors,
        ProjectIdentity projectIdentity
    ) {
        int result = projectIdentity.hashCode();
        result = 31 * result + attributes.hashCode();
        result = 31 * result + capabilitySelectors.hashCode();
        return result;
    }

    @Override
    public ProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    @Override
    public String getDisplayName() {
        return projectIdentity.getDisplayName();
    }

    @Override
    public String getBuildPath() {
        return projectIdentity.getBuildIdentifier().getBuildPath();
    }

    @SuppressWarnings("deprecation")
    @Override
    public String getBuildName() {
        DeprecationLogger.deprecateMethod(ProjectComponentSelector.class, "getBuildName()")
            .withAdvice("Use getBuildPath() to get a unique identifier for the build.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "build_identifier_name_and_current_deprecation")
            .nagUser();
        return DeprecationLogger.whileDisabled(() -> projectIdentity.getBuildIdentifier().getName());
    }

    @Override
    public Path getIdentityPath() {
        return projectIdentity.getBuildTreePath();
    }

    @Override
    public String getProjectPath() {
        return projectIdentity.getProjectPath().getPath();
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifierInternal projectComponentIdentifier = (ProjectComponentIdentifierInternal) identifier;
            return projectComponentIdentifier.getProjectIdentity().equals(projectIdentity);
        }

        return false;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<Capability> getRequestedCapabilities() {
        return capabilitySelectors.stream()
            .filter(c -> c instanceof SpecificCapabilitySelector)
            .map(c -> ((DefaultSpecificCapabilitySelector) c).getBackingCapability())
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableSet<CapabilitySelector> getCapabilitySelectors() {
        return capabilitySelectors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectComponentSelector that = (DefaultProjectComponentSelector) o;
        return projectIdentity.equals(that.projectIdentity) &&
            attributes.equals(that.attributes) &&
            capabilitySelectors.equals(that.capabilitySelectors);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ProjectComponentSelector withAttributes(ProjectComponentSelector selector, ImmutableAttributes attributes) {
        ProjectComponentSelectorInternal current = (ProjectComponentSelectorInternal) selector;
        return new DefaultProjectComponentSelector(
            current.getProjectIdentity(),
            attributes,
            current.getCapabilitySelectors()
        );
    }

    public static ProjectComponentSelector withCapabilities(ProjectComponentSelector selector, ImmutableSet<CapabilitySelector> capabilitySelectors) {
        ProjectComponentSelectorInternal current = (ProjectComponentSelectorInternal) selector;
        return new DefaultProjectComponentSelector(
            current.getProjectIdentity(),
            current.getAttributes(),
            capabilitySelectors
        );
    }

    public static ProjectComponentSelector withAttributesAndCapabilities(ProjectComponentSelector selector, ImmutableAttributes attributes, ImmutableSet<CapabilitySelector> capabilitySelectors) {
        ProjectComponentSelectorInternal current = (ProjectComponentSelectorInternal) selector;
        return new DefaultProjectComponentSelector(
            current.getProjectIdentity(),
            attributes,
            capabilitySelectors
        );
    }

    // TODO: It seems fishy to be able to go directly from a selector to an identifier.
    // There should be some registry involved here.
    public ProjectComponentIdentifier toIdentifier() {
        return new DefaultProjectComponentIdentifier(projectIdentity);
    }
}
