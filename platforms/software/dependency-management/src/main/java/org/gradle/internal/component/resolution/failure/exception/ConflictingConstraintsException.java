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

package org.gradle.internal.component.resolution.failure.exception;

import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure;

import java.util.List;

/**
 * A {@link ComponentSelectionException} that indicates that component selection failed due to multiple
 * conflicting constraints targeting the same module.
 */
public final class ConflictingConstraintsException extends ComponentSelectionException {
    public ConflictingConstraintsException(String message, ModuleRejectedFailure failure, List<String> resolutions) {
        super(message, failure, resolutions);

        LOGGER.info("Conflicting constraints detected: {}", failure.getLegacyErrorMsg());
    }
}
