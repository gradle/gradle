/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.describer;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.component.resolution.failure.exception.ComponentSelectionException;
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure;

import javax.inject.Inject;
import java.util.List;

/**
 * Abstract base class for implementing {@link ResolutionFailureDescriber}s that
 * describe {@link ModuleRejectedFailure}s.
 */
public abstract class ModuleRejectedFailureDescriber extends AbstractResolutionFailureDescriber<ModuleRejectedFailure> {
    private static final String CAPABILITY_CONFLICT_DOCS_ID = "component_capabilities";
    private static final String CAPABILITY_CONFLICT_DOCS_SECTION = "sub:capabilities";
    private static final String CAPABILITY_CONFLICT_RESOLUTION_DOCS_SECTION = "sec:selecting-between-candidates";

    @Inject
    @Override
    protected abstract DocumentationRegistry getDocumentationRegistry();

    @Override
    public AbstractResolutionFailureException describeFailure(ModuleRejectedFailure failure) {
        return new ComponentSelectionException(failure.getLegacyErrorMsg(), failure, buildResolutions(failure));
    }

    private List<String> buildResolutions(ModuleRejectedFailure failure) {
        if (failure.getProblemId() == ResolutionFailureProblemId.CAPABILITY_CONFLICT) {
            DocumentationRegistry docs = getDocumentationRegistry();
            return ImmutableList.of(
                "Capability conflicts are explained in more detail at " + docs.getDocumentationFor(CAPABILITY_CONFLICT_DOCS_ID, CAPABILITY_CONFLICT_DOCS_SECTION) + ".",
                "Use 'resolutionStrategy.capabilitiesResolution' to choose between conflicting capability providers, as described at " + docs.getDocumentationFor(CAPABILITY_CONFLICT_DOCS_ID, CAPABILITY_CONFLICT_RESOLUTION_DOCS_SECTION) + "."
            );
        }
        return failure.getResolutions();
    }
}
