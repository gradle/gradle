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
package org.gradle.internal.execution;

/**
 * Lets internal Gradle code query the output of a project-artifact transform from inside a
 * task action without triggering the "undeclared artifact transform input" deprecation
 * emitted by {@code TransformedProjectArtifactSet}.
 * <p>
 * Open a {@link Scope} via {@link #safely()} around the call site whose input-tracking
 * story is owned by Gradle itself and cannot route through a user task's
 * {@code @InputFiles} property; the suppression is per-thread for the duration of the
 * scope.
 */
public final class SafeInternalArtifactTransformAccess {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private SafeInternalArtifactTransformAccess() {
        throw new UnsupportedOperationException();
    }

    public static Scope safely() {
        DEPTH.set(DEPTH.get() + 1);
        return new Scope();
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            int next = DEPTH.get() - 1;
            if (next == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(next);
            }
        }
    }
}
