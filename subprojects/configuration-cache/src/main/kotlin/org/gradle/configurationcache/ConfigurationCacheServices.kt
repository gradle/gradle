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

package org.gradle.configurationcache

import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.initialization.DefaultConfigurationCacheProblemsListener
import org.gradle.configurationcache.problems.ConfigurationCacheReport
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry


class ConfigurationCacheServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.run {
            add(DefaultBuildTreeModelControllerServices::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheKey::class.java)
            add(ConfigurationCacheReport::class.java)
            add(DefaultConfigurationCacheProblemsListener::class.java)
            add(DefaultBuildModelControllerServices::class.java)
            add(DefaultBuildToolingModelControllerFactory::class.java)
            add(ConfigurationCacheRepository::class.java)
            add(InstrumentedInputAccessListener::class.java)
            add(ConfigurationCacheFingerprintController::class.java)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(RelevantProjectsRegistry::class.java)
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheHost::class.java)
            add(ConfigurationCacheIO::class.java)
        }
    }
}
