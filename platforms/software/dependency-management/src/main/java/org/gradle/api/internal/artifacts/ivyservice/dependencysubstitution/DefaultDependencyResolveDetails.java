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
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.ModuleComponentSelectorParsers;
import org.gradle.internal.Actions;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.typeconversion.NotationParser;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

public class DefaultDependencyResolveDetails implements DependencyResolveDetails {

    private static final NotationParser<Object, ModuleComponentSelector> USE_TARGET_NOTATION_PARSER = ModuleComponentSelectorParsers.parser("useTarget()");

    private final DependencySubstitution delegate;
    private final ModuleVersionIdentifier requested;

    private @Nullable String customDescription;
    private @Nullable VersionConstraint useVersion;
    private @Nullable ModuleComponentSelector useSelector;
    private ComponentSelector target;
    private Action<ArtifactSelectionDetails> artifactSelectionAction = Actions.doNothing();
    private boolean dirty;

    @Inject
    public DefaultDependencyResolveDetails(DependencySubstitutionInternal delegate, ModuleVersionIdentifier requested) {
        this.delegate = delegate;
        this.requested = requested;
        this.target = delegate.getTarget();
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return DefaultModuleVersionSelector.newSelector(requested);
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
        useVersion = null;
        useSelector = USE_TARGET_NOTATION_PARSER.parseNotation(notation);
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

        if (target.equals(delegate.getRequested())) {
            return DefaultModuleVersionSelector.newSelector(requested);
        }
        // The target may already be modified from the original requested
        if (target instanceof ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector((ModuleComponentSelector) target);
        }
        // If the target is a project component, it has not been modified from the requested
        return DefaultModuleVersionSelector.newSelector(requested);
    }

    public void complete() {
        if (!dirty) {
            return;
        }

        if (useSelector != null) {
            useTarget(useSelector);
        } else if (useVersion != null) {
            if (target instanceof ModuleComponentSelector) {
                ModuleComponentSelector moduleSelector = (ModuleComponentSelector) target;
                if (!useVersion.equals(moduleSelector.getVersionConstraint())) {
                    useTarget(DefaultModuleComponentSelector.newSelector(moduleSelector.getModuleIdentifier(), useVersion, moduleSelector.getAttributes(), moduleSelector.getCapabilitySelectors()));
                } else {
                    // Still 'updated' with reason when version remains the same.
                    useTarget(target);
                }
            } else {
                // If the current target is a project component, it must be unmodified from the requested
                ModuleComponentSelector newTarget = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(requested.getGroup(), requested.getName()), useVersion);
                useTarget(newTarget);
            }
        }

        delegate.artifactSelection(artifactSelectionAction);
        dirty = false;
    }

    private void useTarget(ModuleComponentSelector selector) {
        this.target = selector;
        if (customDescription != null) {
            delegate.useTarget(selector, customDescription);
        } else {
            delegate.useTarget(selector);
        }
    }
}
