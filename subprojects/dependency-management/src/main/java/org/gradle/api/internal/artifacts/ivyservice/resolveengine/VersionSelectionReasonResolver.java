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

public class VersionSelectionReasonResolver implements ModuleConflictResolver {

    private final ModuleConflictResolver delegate;

    public VersionSelectionReasonResolver(ModuleConflictResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        delegate.select(details);
        if (!details.hasFailure()) {
            T selected = details.getSelected();
            selected.setSelectionReason(VersionSelectionReasons.withConflictResolution(selected.getSelectionReason()));
        }
    }
}
