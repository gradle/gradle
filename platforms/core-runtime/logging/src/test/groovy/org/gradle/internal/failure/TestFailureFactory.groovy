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

package org.gradle.internal.failure

import com.google.common.collect.ImmutableList
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.problems.failure.DefaultFailure
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory

import static org.gradle.internal.problems.failure.StackTraceRelevance.USER_CODE

class TestFailureFactory implements FailureFactory {

    @Override
    Failure createFailure(Throwable failure) {
        return toFailure(failure)
    }

    private static Failure toFailure(Throwable t) {
        def stack = ImmutableList.copyOf(t.stackTrace)
        def relevances = Collections.nCopies(stack.size(), USER_CODE)
        def causes = getCauses(t).collect { toFailure(it) }
        def suppressed = t.getSuppressed().collect { toFailure(it) }
        new DefaultFailure(t, stack, relevances, suppressed, causes)
    }

    private static List<Throwable> getCauses(Throwable t) {
        if (t instanceof MultiCauseException) {
            return t.causes
        }

        t.getCause() == null ? ImmutableList.of() : ImmutableList.of(t.getCause())
    }
}
