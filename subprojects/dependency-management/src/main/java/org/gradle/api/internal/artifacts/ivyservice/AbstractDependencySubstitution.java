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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

public abstract class AbstractDependencySubstitution<T extends ComponentSelector> implements DependencySubstitutionInternal<T> {
    private final T requested;
    private final ModuleVersionSelector oldRequested;
    private ComponentSelectionReason selectionReason;
    private ComponentSelector target;

    public AbstractDependencySubstitution(T requested, ModuleVersionSelector oldRequested) {
        this.requested = requested;
        this.target = requested;
        this.oldRequested = oldRequested;
    }

    @Override
    public T getRequested() {
        return requested;
    }

    @Override
    public ModuleVersionSelector getOldRequested() {
        return oldRequested;
    }

    @Override
    public void useTarget(Object notation) {
        useTarget(notation, VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public void useTarget(Object notation, ComponentSelectionReason selectionReason) {
        this.target = ComponentSelectorParsers.parser().parseNotation(notation);
        this.selectionReason = selectionReason;
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    @Override
    public ComponentSelector getTarget() {
        return target;
    }

    @Override
    public boolean isUpdated() {
        return selectionReason != null;
    }
}
