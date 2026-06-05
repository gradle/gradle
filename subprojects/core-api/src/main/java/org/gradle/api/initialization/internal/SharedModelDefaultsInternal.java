/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.initialization.internal;

import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.EcosystemApplyAction;
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Build.class)
public interface SharedModelDefaultsInternal extends SharedModelDefaults {

    /**
     * Registers an ecosystem whose {@link EcosystemApplyAction#apply(SharedModelDefaults)} is run (at most
     * once) when shared model defaults are processed, at settings time, after feature discovery. Registering
     * the same ecosystem class more than once has no additional effect.
     */
    void registerEcosystem(Class<? extends EcosystemApplyAction> ecosystemClass);

    /**
     * Specifies the current project when interpreting defaults configuration
     * blocks during project evaluation time.
     */
    void setProjectLayout(ProjectLayout projectLayout);

    /**
     * Clears the current project when the interpretation of defaults configuration
     * blocks is finished during project evaluation time.
     */
    void clearProjectLayout();

    /**
     * Processes all registered defaults.  This should be called after all settings evaluated hooks have been applied
     * but before build script evaluation starts.
     */
    void processRegistrations();
}
