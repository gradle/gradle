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

import org.gradle.api.internal.SettingsInternal
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.initialization.ConfigurationCacheProblemsListener
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.initialization.DefaultConfigurationCacheProblemsListener
import org.gradle.configurationcache.initialization.DefaultInjectedClasspathInstrumentationStrategy
import org.gradle.configurationcache.initialization.NoOpConfigurationCacheProblemsListener
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


class ConfigurationCacheServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            add(BuildTreeListenerManager::class.java)
            add(ConfigurationCacheStartParameter::class.java)
            add(DefaultInjectedClasspathInstrumentationStrategy::class.java)
            add(ConfigurationCacheKey::class.java)
            add(ConfigurationCacheReport::class.java)
            add(ConfigurationCacheProblems::class.java)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            add(ConfigurationCacheBuildScopeListenerManagerAction::class.java)
            add(SystemPropertyAccessListener::class.java)
            add(RelevantProjectsRegistry::class.java)
            add(ConfigurationCacheFingerprintController::class.java)
            addProvider(BuildServicesProvider())
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheRepository::class.java)
            add(ConfigurationCacheHost::class.java)
            add(DefaultConfigurationCache::class.java)
        }
    }
}


class BuildServicesProvider {
    fun createConfigurationCacheProblemsListener(
        buildPath: PublicBuildPath,
        startParameter: ConfigurationCacheStartParameter,
        problemsListener: ConfigurationCacheProblems,
        userCodeApplicationContext: UserCodeApplicationContext
    ): ConfigurationCacheProblemsListener {
        if (!startParameter.isEnabled || buildPath.buildPath.name == SettingsInternal.BUILD_SRC) {
            return NoOpConfigurationCacheProblemsListener()
        } else {
            return DefaultConfigurationCacheProblemsListener(problemsListener, userCodeApplicationContext)
        }
    }
}


@ServiceScope(Scopes.BuildTree::class)
class BuildTreeListenerManager(
    val service: ListenerManager
)
