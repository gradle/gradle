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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

class CachedModuleArtifactsKey {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final String context;

    public CachedModuleArtifactsKey(ModuleVersionIdentifier moduleVersionIdentifier, String context) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CachedModuleArtifactsKey that = (CachedModuleArtifactsKey) o;
        return context.equals(that.context) && moduleVersionIdentifier.equals(that.moduleVersionIdentifier);

    }

    @Override
    public int hashCode() {
        int result = moduleVersionIdentifier.hashCode();
        result = 31 * result + context.hashCode();
        return result;
    }
}
