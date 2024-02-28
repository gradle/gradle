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

import org.gradle.internal.component.resolution.failure.exception.ArtifactVariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;

import java.util.Collections;

/**
 * This type is {@code deprecated} and will be removed in Gradle 9.0.
 *
 * This is temporarily available for migration only.
 * This exception class is internal and has been replaced by {@link ArtifactVariantSelectionException}, which is also internal.
 * If possible, catch a {@link RuntimeException} instead to avoid depending on Gradle internal classes.
 */
@Deprecated
public abstract class AmbiguousVariantSelectionException extends ArtifactVariantSelectionException {
    private static final ResolutionFailure EMPTY_RESOLUTION_FAILURE = () -> "Empty failure";

    public AmbiguousVariantSelectionException(String message) {
        super(message, EMPTY_RESOLUTION_FAILURE, Collections.emptyList());
    }
}
