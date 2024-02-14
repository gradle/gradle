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
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.exceptions.StyledException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for all attribute matching selection failures occurring at any stage of dependency resolution.
 *
 * @implNote This class should not be subclassed beyond the existing
 * {@link ConfigurationSelectionException}, {@link ArtifactVariantSelectionException}, and
 * {@link VariantSelectionException} subtypes.
 */
@Contextual
public abstract class AbstractResolutionFailureException extends StyledException implements ResolutionProvider {
    private final List<String> resolutions = new ArrayList<>(1); // Usually there is only one resolution

    public AbstractResolutionFailureException(String message) {
        this(message, null);
    }

    public AbstractResolutionFailureException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * Adds a resolution to the list of resolutions.
     *
     * Meant to be called during subclass construction, so <strong>must</strong> remain safe to do so by only accessing fields on this type,
     * hence the {@code final} modifier.
     *
     * @param resolution The resolution (suggestion message) to add
     */
    public final void addResolution(String resolution) {
        resolutions.add(resolution);
    }

    @Override
    public ImmutableList<String> getResolutions() {
        return ImmutableList.copyOf(resolutions);
    }
}
