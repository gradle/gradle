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

package org.gradle.internal.fingerprint;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Shares {@link RootFingerprint}s between all file collection fingerprints that contain the same root
 * with the same content, captured with the same fingerprinting strategy configuration.
 *
 * Shared instances stay available for as long as some fingerprint refers to them.
 */
@ServiceScope(Scope.Global.class)
public interface RootFingerprintInterner {
    @Nullable
    RootFingerprint find(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash);

    /**
     * Returns the shared instance for the given root, creating it with the supplier if there is none yet.
     */
    RootFingerprint intern(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash, Supplier<RootFingerprint> factory);
}
