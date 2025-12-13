/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when some internal exception occurs executing a test suite.
 */
@NullMarked
public class TestSuiteExecutionException extends GradleException implements ResolutionProvider {
    public TestSuiteExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public List<String> getResolutions() {
        if (getCause() instanceof ResolutionProvider) {
            return ((ResolutionProvider) getCause()).getResolutions();
        } else {
            return Collections.emptyList();
        }
    }
}
