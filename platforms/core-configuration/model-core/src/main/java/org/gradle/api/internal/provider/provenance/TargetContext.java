/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider.provenance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The project-owned model being configured, independently of the configuring source scope.
 */
public final class TargetContext {
    private final ScopeIdentity owner;
    private final String modelPath;

    public TargetContext(ScopeIdentity owner, String modelPath) {
        this.owner = Objects.requireNonNull(owner);
        this.modelPath = Objects.requireNonNull(modelPath);
    }

    public ScopeIdentity getOwner() {
        return owner;
    }

    public String getModelPath() {
        return modelPath;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TargetContext)) {
            return false;
        }
        TargetContext that = (TargetContext) other;
        return Objects.equals(owner, that.owner)
            && Objects.equals(modelPath, that.modelPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, modelPath);
    }
}
