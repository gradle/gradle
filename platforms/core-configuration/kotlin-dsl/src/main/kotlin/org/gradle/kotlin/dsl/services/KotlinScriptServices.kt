/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.services

import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractGradleModuleServices


internal
class KotlinScriptServices : AbstractGradleModuleServices() {

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(org.gradle.kotlin.dsl.accessors.BuildScopeServices)
        registration.addProvider(org.gradle.kotlin.dsl.concurrent.BuildServices)
        registration.addProvider(org.gradle.kotlin.dsl.provider.BuildServices)
    }

    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.addProvider(org.gradle.kotlin.dsl.support.GlobalServices)
    }

    override fun registerGradleUserHomeServices(registration: ServiceRegistration) {
        registration.addProvider(org.gradle.kotlin.dsl.cache.GradleUserHomeServices)
        registration.addProvider(org.gradle.kotlin.dsl.support.GradleUserHomeServices)
        registration.addProvider(org.gradle.kotlin.dsl.provider.GradleUserHomeServices)
    }
}
