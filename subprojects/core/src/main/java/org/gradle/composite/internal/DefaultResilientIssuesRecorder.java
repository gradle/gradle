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

package org.gradle.composite.internal;

import org.gradle.composite.ResilientIssuesRecorder;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@NullMarked
public class DefaultResilientIssuesRecorder implements ResilientIssuesRecorder {
    private final Set<Failure> failures = new HashSet<>();
    private final FailureFactory failureFactory;

    public DefaultResilientIssuesRecorder(FailureFactory failureFactory){
        this.failureFactory = failureFactory;
    }

    @Override
    public void recordResilientIssue(Throwable throwable) {
        failures.add(failureFactory.create(throwable));
    }

    @Override
    public void recordResilientIssue(Failure failure) {
        failures.add(failure);
    }

    @Override
    public Collection<Failure> getFailures() {
        return failures;
    }

    @Override
    public void clear() {
        failures.clear();
    }
}
