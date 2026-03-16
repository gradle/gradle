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

package org.gradle.api.internal.project;

/**
 * A thread-local depth counter used to suppress re-entrant Isolated Projects
 * property-API violation reports in {@link DefaultProject}.
 *
 * <p>When {@link MutableStateAccessAwareProject} delegates a
 * {@code property}/{@code findProperty}/{@code hasProperty}/{@code getProperties}
 * call to the underlying {@link DefaultProject}, it first increments this counter
 * so that {@code DefaultProject} skips its own reporting. The cross-project
 * violation has already been reported by {@code MutableStateAccessAwareProject}.</p>
 */
class CrossProjectPropertyApiAccessFlag {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private CrossProjectPropertyApiAccessFlag() {}

    static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    static void leave() {
        DEPTH.set(DEPTH.get() - 1);
    }

    static boolean isNotActive() {
        return DEPTH.get() <= 0;
    }
}
