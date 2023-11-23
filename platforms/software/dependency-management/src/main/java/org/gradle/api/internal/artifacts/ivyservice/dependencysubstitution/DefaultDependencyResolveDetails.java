/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.Actions;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;

public class DefaultDependencyResolveDetails implements DependencyResolveDetails {

    private static final NotationParser<Object, ModuleVersionSelector> USE_TARGET_NOTATION_PARSER = ModuleVersionSelectorParsers.parser("useTarget()");

    private final DependencySubstitutionInternal delegate;
    private final ModuleVersionSelector requested;

    private String customDescription;
    private VersionConstraint useVersion;
    private ModuleComponentSelector useSelector;
    private Action<ArtifactSelectionDetails> artifactSelectionAction = Actions.doNothing();
    private boolean dirty;

    @Inject
    public DefaultDependencyResolveDetails(DependencySubstitutionInternal delegate, ModuleVersionSelector requested) {
        this.delegate = delegate;
        this.requested = requested;
    }

    private ComponentSelectionDescriptorInternal selectionReason() {
        return customDescription == null
            ? ComponentSelectionReasons.SELECTED_BY_RULE
            : ComponentSelectionReasons.SELECTED_BY_RULE.withDescription(Describables.of(customDescription));
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public void useVersion(String version) {
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }
        useSelector = null;
        useVersion = new DefaultMutableVersionConstraint(version);
        dirty = true;
   }

    @Override
    public void useTarget(Object notation) {
        ModuleVersionSelector newTarget = USE_TARGET_NOTATION_PARSER.parseNotation(notation);
        useVersion = null;
        useSelector = DefaultModuleComponentSelector.newSelector(newTarget);
        dirty = true;
    }

    @Override
    public DependencyResolveDetails because(String description) {
        customDescription = description;
        dirty = true;
        return this;
    }

    @Override
    public DependencyResolveDetails artifactSelection(Action<? super ArtifactSelectionDetails> configurationAction) {
        artifactSelectionAction = Actions.composite(artifactSelectionAction, configurationAction);
        dirty = true;
        return this;
    }

    @Override
    public ModuleVersionSelector getTarget() {
        complete();

        if (delegate.getTarget().equals(delegate.getRequested())) {
            return requested;
        }
        // The target may already be modified from the original requested
        if (delegate.getTarget() instanceof ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector((ModuleComponentSelector) delegate.getTarget());
        }
        // If the target is a project component, it has not been modified from the requested
        return requested;
    }

    public void complete() {
        if (!dirty) {
            return;
        }

        ComponentSelectionDescriptorInternal selectionReason = selectionReason();

        if (useSelector != null) {
            delegate.useTarget(useSelector, selectionReason);
        } else if (useVersion != null) {
            if (delegate.getTarget() instanceof ModuleComponentSelector) {
                ModuleComponentSelector target = (ModuleComponentSelector) delegate.getTarget();
                if (!useVersion.equals(target.getVersionConstraint())) {
                    delegate.useTarget(DefaultModuleComponentSelector.newSelector(target.getModuleIdentifier(), useVersion, target.getAttributes(), target.getRequestedCapabilities()), selectionReason);
                } else {
                    // Still 'updated' with reason when version remains the same.
                    delegate.useTarget(delegate.getTarget(), selectionReason);
                }
            } else {
                // If the current target is a project component, it must be unmodified from the requested
                ModuleComponentSelector newTarget = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(requested.getGroup(), requested.getName()), useVersion);
                delegate.useTarget(newTarget, selectionReason);
            }
        }

        delegate.artifactSelection(artifactSelectionAction);
        dirty = false;
    }
}
