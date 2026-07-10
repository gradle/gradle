/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException;
import org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.Collections;

/**
 * This type is {@code deprecated} and will be removed in Gradle 10.
 * <p>
 * This is used by the oss-licenses plugin that is used by Now In Android.  To remove this class, we must first
 * address that with a new version of oss-licenses, or the removal of it from NiA.
 * See <a href="https://github.com/google/play-services-plugins/pull/305">this PR</a>.
 * <p>
 * This is temporarily available for migration only.
 * This exception class is internal and has been replaced by {@link ArtifactSelectionException}, which is also internal.
 * If possible, catch a {@link RuntimeException} instead to avoid depending on Gradle internal classes.
 */
@Deprecated
public abstract class AmbiguousVariantSelectionException extends ArtifactSelectionException {
    private static final ArtifactSelectionFailure EMPTY_RESOLUTION_FAILURE = new ArtifactSelectionFailure() {
        @Override
        public ComponentIdentifier getTargetComponent() {
            return () -> "empty component";
        }

        @Override
        public String getTargetVariant() {
            return "empty variant";
        }

        @Override
        public ImmutableAttributes getRequestedAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public String describeRequestTarget() {
            return "empty target";
        }

        @Override
        public ResolutionFailureProblemId getProblemId() {
            return ResolutionFailureProblemId.UNKNOWN_RESOLUTION_FAILURE;
        }
    };

    public AmbiguousVariantSelectionException(String message) {
        super(message, EMPTY_RESOLUTION_FAILURE, Collections.emptyList());

        DeprecationLogger.deprecateType(AmbiguousVariantSelectionException.class)
            .withAdvice("The " + AmbiguousVariantSelectionException.class.getName() + " type is temporarily available for migration only.")
            .willBeRemovedInGradle10()
            .withUserManual("feature_lifecycle", "sec:internal")
            .nagUser();
    }
}
