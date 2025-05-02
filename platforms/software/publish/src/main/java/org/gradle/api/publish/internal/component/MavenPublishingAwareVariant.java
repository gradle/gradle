/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.publish.internal.component;

import org.gradle.api.component.SoftwareComponentVariant;

public interface MavenPublishingAwareVariant extends SoftwareComponentVariant {
    ScopeMapping getScopeMapping();

    // Order is important!
    enum ScopeMapping {
        compile("compile", false),
        runtime("runtime", false),
        compile_optional("compile", true),
        runtime_optional("runtime", true);

        private final String scope;
        private final boolean optional;

        ScopeMapping(String scope, boolean optional) {
            this.scope = scope;
            this.optional = optional;
        }

        public String getScope() {
            return scope;
        }

        public boolean isOptional() {
            return optional;
        }

        public static ScopeMapping of(String scope, boolean optional) {
            if (optional) {
                scope += "_optional";
            }
            return ScopeMapping.valueOf(scope);
        }
    }

    static ScopeMapping scopeForVariant(SoftwareComponentVariant variant) {
        if (variant instanceof MavenPublishingAwareVariant) {
            return ((MavenPublishingAwareVariant) variant).getScopeMapping();
        }
        // TODO: Update native plugins to use maven-aware variants so we can remove this.
        String name = variant.getName();
        if ("api".equals(name) || "apiElements".equals(name)) {
            return ScopeMapping.compile;
        }
        return ScopeMapping.runtime;
    }
}
