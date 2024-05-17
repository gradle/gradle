/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Contributes build scoped services.
 */
@ServiceScope(Scope.BuildTree.class)
public interface BuildModelControllerServices {
    /**
     * Provides an action to make the following services available in the {@link Scope.Build} scope:
     *
     * <ul>
     *     <li>{@link BuildLifecycleController}</li>
     *     <li>{@link BuildState}</li>
     *     <li>{@link BuildDefinition}</li>
     *     <li>{@link org.gradle.api.internal.GradleInternal}</li>
     * </ul>
     */
    Supplier servicesForBuild(BuildDefinition buildDefinition, BuildState owner, @Nullable BuildState parentBuild);

    interface Supplier {
        void applyServicesTo(ServiceRegistration registration, BuildScopeServices services);
    }
}
