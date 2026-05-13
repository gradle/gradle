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
package org.gradle.tooling.internal.consumer.daemon;

/**
 * Stable enum view of the daemon's runtime state.
 *
 * <p>Mirrors the historical {@code DaemonState} (formerly {@code State}) enum
 * by ordinal for the values that have been stable across all supported daemon
 * versions (5.0+). Ordinals beyond {@link #STOPPED} have shifted (e.g. {@code
 * ForceStopped} was inserted in Gradle 9) so anything past ordinal 4 surfaces
 * as {@link #UNKNOWN}.
 */
enum DaemonStateView {
    IDLE,
    BUSY,
    CANCELED,
    STOP_REQUESTED,
    STOPPED,
    UNKNOWN;

    static DaemonStateView fromOrdinal(int ordinal) {
        switch (ordinal) {
            case 0: return IDLE;
            case 1: return BUSY;
            case 2: return CANCELED;
            case 3: return STOP_REQUESTED;
            case 4: return STOPPED;
            default: return UNKNOWN;
        }
    }
}
