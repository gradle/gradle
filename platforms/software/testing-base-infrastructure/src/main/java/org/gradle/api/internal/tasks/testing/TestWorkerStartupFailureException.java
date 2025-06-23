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

package org.gradle.api.internal.tasks.testing;

import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Thrown when a test worker completely fails to start.
 * <p>
 * This exception is used to provide additional information about the failure, including possible resolutions.
 */
@NullMarked
public final class TestWorkerStartupFailureException extends DefaultMultiCauseException implements ResolutionProvider {
    private final List<String> resolutions;

    public TestWorkerStartupFailureException(String message, List<Throwable> causes, List<String> resolutions) {
        super(message, causes);
        this.resolutions = resolutions;
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
