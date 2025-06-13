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

import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link GradleException} thrown when a test framework completely fails to start.
 * <p>
 * This exception is used to provide additional information about the failure, including possible resolutions.
 */
@NullMarked
public final class TestFrameworkStartupFailureException extends GradleException implements ResolutionProvider {
    private final List<String> resolutions;

    public TestFrameworkStartupFailureException(TestFailure testFailure) {
        super(testFailure.getDetails().getMessage() != null ? testFailure.getDetails().getMessage() : "", testFailure.getRawFailure());

        if (testFailure.getRawFailure() instanceof ResolutionProvider && !((ResolutionProvider) testFailure.getRawFailure()).getResolutions().isEmpty()) {
            this.resolutions = new ArrayList<>(((ResolutionProvider) testFailure.getRawFailure()).getResolutions());
        } else {
            this.resolutions = Collections.singletonList("Inspect your task configuration for errors.");
        }
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
