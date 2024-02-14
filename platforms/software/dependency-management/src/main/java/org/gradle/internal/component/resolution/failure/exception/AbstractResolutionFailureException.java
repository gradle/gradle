/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.exception;

import com.google.common.collect.ImmutableList;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.exceptions.StyledException;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Abstract base class for all attribute matching selection failures occurring at any stage of dependency resolution.
 *
 * This exception type carries information about the failure, and implements {@link ResolutionProvider} to provide a
 * list of resolutions that may help the user to fix the problem.  This class is meant to be immutable.
 *
 * @implNote This class should not be subclassed beyond the existing
 * {@link ConfigurationSelectionException}, {@link ArtifactVariantSelectionException}, and
 * {@link VariantSelectionException} subtypes.  All subtypes should remain immutable.
 */
@Contextual
public abstract class AbstractResolutionFailureException extends StyledException implements ResolutionProvider {
    private static final Logger LOGGER = Logging.getLogger(AbstractResolutionFailureException.class);

    private final ImmutableList<String> resolutions;
    private final ResolutionFailure failure;

    public AbstractResolutionFailureException(String message, ResolutionFailure failure, List<String> resolutions) {
        this(message, failure, resolutions, null);
    }

    public AbstractResolutionFailureException(String message, ResolutionFailure failure, List<String> resolutions, @Nullable Throwable cause) {
        super(message, cause);
        this.failure = failure;
        this.resolutions = ImmutableList.copyOf(resolutions);

        LOGGER.info("Variant Selection Exception: {} caused by Resolution Failure: {}", this.getClass().getName(), failure.getClass().getName());
    }

    public ResolutionFailure getFailure() {
        return failure;
    }

    @Override
    public ImmutableList<String> getResolutions() {
        return resolutions;
    }
}
