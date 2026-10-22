/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.problems;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Captures a cheap, bounded view of the calling thread's stack, enough for the location analyser to
 * infer where a problem originates.
 *
 * <p>Full stack capture is why problem locations are capped: doing it for every problem is too
 * costly. This service provides a location past that cap, for an unbounded number of problems, by
 * capturing only as much of the stack as the location needs.</p>
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public interface BoundedCallerStackCapturer {

    /**
     * Returns a throwable whose stack trace is a bounded prefix of the calling thread's stack, or
     * {@code null} if no user-code frame was found.
     */
    @Nullable
    Throwable captureCallerStack();
}
