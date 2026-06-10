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
 * Thread-local marker indicating that internal Gradle code is intentionally querying the
 * output of an artifact transform from a task action without declaring it as a task input
 * through the usual mechanism. Suppresses the "undeclared artifact transform input"
 * deprecation emitted from
 * {@code org.gradle.api.internal.artifacts.transform.TransformedProjectArtifactSet} for
 * the duration of the returned {@link Scope}.
 *
 * <p>Intended for narrow internal use sites such as the script classpath resolver, which
 * has its own input-tracking story and cannot route the result through a user task's
 * {@code @InputFiles} property.
 *
 * <p>Suppression is per-thread; if the artifact set's visit dispatches work to another
 * thread, the scope on the entering thread will not propagate. The known consumer
 * ({@code DefaultScriptClassPathResolver}) visits artifacts on the same thread that opens
 * the scope, so this is not a concern in practice today; revisit if new entry points are
 * added that visit on worker threads.
 */
public final class AcceptedArtifactTransformAccess {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private AcceptedArtifactTransformAccess() {}

    public static Scope enter() {
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
