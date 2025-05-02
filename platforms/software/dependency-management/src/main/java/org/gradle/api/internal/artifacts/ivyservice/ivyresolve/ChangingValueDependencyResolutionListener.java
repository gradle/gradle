/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;

import java.util.Set;

/**
 * Notified of the use of changing values during dependency resolution, so this can be noted in the configuration cache inputs
 */
@EventScope(Scope.Build.class)
public interface ChangingValueDependencyResolutionListener {
    ChangingValueDependencyResolutionListener NO_OP = new ChangingValueDependencyResolutionListener() {
        @Override
        public void onDynamicVersionSelection(ModuleComponentSelector requested, CacheExpirationControl.Expiry expiry, Set<ModuleVersionIdentifier> versions) {
        }

        @Override
        public void onChangingModuleResolve(ModuleComponentIdentifier moduleId, CacheExpirationControl.Expiry expiry) {
        }
    };

    /**
     * Called when a dynamic version is selected using the set of candidate versions queried from a repository.
     */
    void onDynamicVersionSelection(ModuleComponentSelector requested, CacheExpirationControl.Expiry expiry, Set<ModuleVersionIdentifier> versions);

    /**
     * Called when a changing artifact is resolved using the artifact state queried from a repository.
     */
    void onChangingModuleResolve(ModuleComponentIdentifier moduleId, CacheExpirationControl.Expiry expiry);
}
