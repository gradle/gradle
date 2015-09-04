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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import java.util.Collection;

public class VersionSelectionReasonResolver implements ModuleConflictResolver {

    private final ModuleConflictResolver delegate;

    public VersionSelectionReasonResolver(ModuleConflictResolver delegate) {
        this.delegate = delegate;
    }

    public <T extends ComponentResolutionState> T select(Collection<? extends T> candidates) {
        T selected = delegate.select(candidates);
        selected.setSelectionReason(VersionSelectionReasons.withConflictResolution(selected.getSelectionReason()));
        return selected;
    }
}
