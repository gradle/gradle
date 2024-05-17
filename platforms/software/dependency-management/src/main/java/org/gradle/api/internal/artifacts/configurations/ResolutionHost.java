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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import java.util.Collection;
import java.util.Optional;

/**
 * The "Host" or owner of a resolution -- the thing in charge of the resolution, or the thing being resolved.
 *
 * <p>The purpose of this type is to be a configuration-cache compatible representation of the thing
 * being resolved. This type should remain as minimal as possible.</p>
 */
public interface ResolutionHost {

    DisplayName displayName();

    default String getDisplayName() {
        return displayName().getDisplayName();
    }

    default DisplayName displayName(String type) {
        return Describables.of(displayName(), type);
    }

    /**
     * Rethrows the provided failures. Does nothing if the list of failures is empty.
     */
    default void rethrowFailure(String type, Collection<Throwable> failures) {
        mapFailure(type, failures).ifPresent(e -> {
            throw e;
        });
    }

    Optional<? extends ResolveException> mapFailure(String type, Collection<Throwable> failures);
}
