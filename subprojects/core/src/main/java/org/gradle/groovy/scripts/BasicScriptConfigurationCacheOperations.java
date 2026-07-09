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

package org.gradle.groovy.scripts;

import org.jspecify.annotations.NullMarked;

/**
 * Internal seam that lets the configuration cache prepare a reconstructed {@link BasicScript} for
 * reuse without exposing the operation on the script's DSL-visible surface. The operation itself is
 * package-private on {@link BasicScript}; this class (in the same, non-public package) makes it
 * reachable from the serialization codecs.
 * See <a href="https://github.com/gradle/gradle/issues/20126">#20126</a>.
 */
@NullMarked
public final class BasicScriptConfigurationCacheOperations {

    private BasicScriptConfigurationCacheOperations() {
    }

    /**
     * Replaces the script's target with {@code brokenTarget}, so build-model access from a closure
     * owned by the (retained) script fails with a clear problem rather than resolving live state.
     * The script's own services are restored separately, by serializing their codec-backed values.
     */
    public static void installBrokenTarget(BasicScript script, Object brokenTarget) {
        script.installBrokenTargetForConfigurationCache(brokenTarget);
    }
}
