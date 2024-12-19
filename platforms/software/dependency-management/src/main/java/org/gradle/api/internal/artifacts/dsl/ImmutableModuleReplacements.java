/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import javax.annotation.Nullable;

/**
 * Immutable user-configured description of which modules replace other
 * modules in a dependency graph.
 *
 * @see DependencyHandler#getModules()
 */
public class ImmutableModuleReplacements {

    private final ImmutableMap<ModuleIdentifier, Replacement> replacements;

    public ImmutableModuleReplacements(ImmutableMap<ModuleIdentifier, Replacement> replacements) {
        this.replacements = replacements;
    }

    @Nullable public Replacement getReplacementFor(ModuleIdentifier sourceModule) {
        return replacements.get(sourceModule);
    }

    public static class Replacement {
        private final ModuleIdentifier target;
        private final String reason;

        Replacement(ModuleIdentifier target, @Nullable String reason) {
            this.target = target;
            this.reason = reason;
        }

        public ModuleIdentifier getTarget() {
            return target;
        }

        @Nullable
        public String getReason() {
            return reason;
        }
    }
}
