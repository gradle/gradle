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

package org.gradle.api.internal.artifacts.repositories.distribution;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * All modules from the Gradle distribution that are available to depend on
 * via dependency management.
 */
@ServiceScope(Scope.Global.class)
public interface AvailableDistributionModules {

    /**
     * Get the distribution module identified by the given ID, or null
     * if no module with that ID exists, or the distribution module with
     * that ID is not available.
     */
    @Nullable AvailableModule getModule(ModuleComponentIdentifier id);

    /**
     * Get the version of the distribution module belonging to the module
     * with the given ID, or null if no such available distribution module
     * exists.
     */
    @Nullable String getModuleVersion(ModuleIdentifier moduleId);

    /**
     * A distribution module that is available to depend on by the user.
     */
    interface AvailableModule {

        /**
         * The internal name of the module.
         */
        String getName();

        /**
         * The public alias for the module. This is the ID that the user
         * identifies this module with.
         */
        ModuleComponentIdentifier getId();

        /**
         * Get the classpath that implements this module.
         */
        ClassPath getImplementationClasspath();

        /**
         * Get all first-level modules that this module depends on.
         */
        List<AvailableModule> getDependencies();

    }

}
