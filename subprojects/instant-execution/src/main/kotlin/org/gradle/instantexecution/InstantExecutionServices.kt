/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.internal.SettingsInternal
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprintController
import org.gradle.instantexecution.initialization.DefaultInstantExecutionProblemsListener
import org.gradle.instantexecution.initialization.InstantExecutionProblemsListener
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.initialization.NoOpInstantExecutionProblemsListener
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


class InstantExecutionServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            add(BuildTreeListenerManager::class.java)
            add(InstantExecutionStartParameter::class.java)
            add(InstantExecutionCacheKey::class.java)
            add(InstantExecutionReport::class.java)
            add(InstantExecutionProblems::class.java)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(InstantExecutionClassLoaderScopeRegistryListener::class.java)
            add(InstantExecutionBuildScopeListenerManagerAction::class.java)
            add(SystemPropertyAccessListener::class.java)
            add(RelevantProjectsRegistry::class.java)
            add(InstantExecutionCacheFingerprintController::class.java)
            addProvider(BuildServicesProvider())
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(InstantExecutionCache::class.java)
            add(InstantExecutionHost::class.java)
            add(DefaultInstantExecution::class.java)
        }
    }
}


class BuildServicesProvider {
    fun createInstantExecutionProblemsListener(
        buildPath: PublicBuildPath,
        startParameter: InstantExecutionStartParameter,
        problemsListener: InstantExecutionProblems,
        userCodeApplicationContext: UserCodeApplicationContext
    ): InstantExecutionProblemsListener {
        if (!startParameter.isEnabled || buildPath.buildPath.name == SettingsInternal.BUILD_SRC) {
            return NoOpInstantExecutionProblemsListener()
        } else {
            return DefaultInstantExecutionProblemsListener(problemsListener, userCodeApplicationContext)
        }
    }
}


@ServiceScope(Scopes.BuildTree)
class BuildTreeListenerManager(
    val service: ListenerManager
)
