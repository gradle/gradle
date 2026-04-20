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
package org.gradle.api.tasks.diagnostics.internal.repositories.reachability;

import org.jspecify.annotations.NullMarked;

/**
 * Result of a lightweight reachability probe for a repository URL.
 *
 * <p>The probe is executed at task-execution time for unique remote repository URLs.
 * Local repositories (e.g. {@code mavenLocal()}, {@code flatDir}) are never probed
 * and consequently never carry a reachability status.
 */
@NullMarked
public enum ReachabilityStatus {
    /** URL returned 2xx or 3xx — no marker. */
    REACHABLE,
    /** URL could not be contacted (DNS, connect refused, timeout, 4xx other than auth, 5xx). */
    UNREACHABLE,
    /** URL returned 401 or 403. */
    UNAUTHORIZED,
    /** URL could not be parsed as a URI. */
    MALFORMED_URL,
    /** Build was run with {@code --offline} — no probes were performed. */
    OFFLINE_SKIPPED
}
