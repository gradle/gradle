/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins

import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.kotlin.dsl.provider.plugins.precompiled.DefaultPrecompiledScriptPluginsSupport


class KotlinDslProviderServices : AbstractGradleModuleServices() {

    override fun registerGradleUserHomeServices(registration: ServiceRegistration) {
        registration.addProvider(GradleUserHomeServices)
    }
}


internal
object GradleUserHomeServices : ServiceRegistrationProvider {

    @Provides
    fun createProjectSchemaProvider() =
        DefaultProjectSchemaProvider()

    @Provides
    fun createKotlinScriptBasePluginsApplicator() =
        DefaultKotlinScriptBasePluginsApplicator()

    @Provides
    fun createPrecompiledScriptPluginsSupport() =
        DefaultPrecompiledScriptPluginsSupport()
}
