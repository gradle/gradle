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

package org.gradle.internal.cc.impl

import org.gradle.internal.buildtree.BuildModelParametersFactory
import org.gradle.internal.buildtree.control.DefaultBuildModelParametersFactory
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices


class ConfigurationCacheServices : AbstractGradleModuleServices() {

    override fun registerGlobalServices(registration: ServiceRegistration) = with(registration) {
        // ALL MODES
        add(BuildModelParametersFactory::class.java, DefaultBuildModelParametersFactory::class.java)
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) = with(registration) {
        // ALL MODES

        // These two services ensure cleanup of stale CC entries,
        // which must be scheduled regardless of whether CC is used in the current invocation
        add(ConfigurationCacheRepository::class.java)
        add(ConfigurationCacheEntryCollector::class.java)
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.addProvider(BuildTreeModelControllerServices)
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildModelControllerServices)
    }
}
