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

import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprintController
import org.gradle.instantexecution.initialization.DefaultInstantExecutionProblemsListener
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry


class InstantExecutionServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(InstantExecutionStartParameter::class.java)
            add(InstantExecutionProblems::class.java)
            add(InstantExecutionReport::class.java)
            add(InstantExecutionClassLoaderScopeRegistryListener::class.java)
            add(InstantExecutionBuildScopeListenerManagerAction::class.java)
            add(DefaultInstantExecutionProblemsListener::class.java)
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(InstantExecutionCacheFingerprintController::class.java)
            add(InstantExecutionHost::class.java)
            add(DefaultInstantExecution::class.java)
        }
    }
}
