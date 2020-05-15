/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;

import java.util.List;

class NoOpSubstitution implements DependencySubstitutionInternal {
    public static final NoOpSubstitution INSTANCE = new NoOpSubstitution();

    private NoOpSubstitution() {

    }

    @Override
    public void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor) {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public ComponentSelector getTarget() {
        throw methodShouldNotHaveBeenCalled();
    }

    private UnsupportedOperationException methodShouldNotHaveBeenCalled() {
        return new UnsupportedOperationException("Shouldn't be called");
    }

    @Override
    public List<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public boolean isUpdated() {
        return false;
    }

    @Override
    public ArtifactSelectionDetailsInternal getArtifactSelectionDetails() {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public ComponentSelector getRequested() {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public void useTarget(Object notation) {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public void useTarget(Object notation, String reason) {
        throw methodShouldNotHaveBeenCalled();
    }

    @Override
    public void artifactSelection(Action<? super ArtifactSelectionDetails> action) {
        throw methodShouldNotHaveBeenCalled();
    }
}
