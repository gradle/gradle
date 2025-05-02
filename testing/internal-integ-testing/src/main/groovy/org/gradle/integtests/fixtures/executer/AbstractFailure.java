/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import junit.framework.AssertionFailedError;

import java.util.List;

abstract class AbstractFailure implements ExecutionFailure.Failure {

    final String description;
    final List<String> causes;

    public AbstractFailure(String description, List<String> causes) {
        this.description = description;
        this.causes = causes;
    }

    @Override
    public void assertHasCause(String message) {
        if (!causes.contains(message)) {
            throw new AssertionFailedError(String.format("Expected cause '%s' not found in %s", message, causes));
        }
    }

    @Override
    public void assertHasFirstCause(String message) {
        if (causes.isEmpty()) {
            throw new AssertionFailedError(String.format("Expected first cause '%s', got none", message));
        }
        String firstCause = causes.get(0);
        if (!firstCause.equals(message)) {
            throw new AssertionFailedError(String.format("Expected first cause '%s', got '%s'", message, firstCause));
        }
    }

    @Override
    public void assertHasCauses(int count) {
        if (causes.size() != count) {
            throw new AssertionFailedError(String.format("Expecting %d cause(s), got %d", count, causes.size()));
        }
    }
}
