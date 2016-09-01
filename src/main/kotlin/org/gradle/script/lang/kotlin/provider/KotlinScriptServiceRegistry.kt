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

package org.gradle.script.lang.kotlin.provider

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.cache.GeneratedGradleJarCache

import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.PluginServiceRegistry

class KotlinScriptServiceRegistry : PluginServiceRegistry {

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(KotlinScriptBuildServices)
    }

    override fun registerGlobalServices(registration: ServiceRegistration) {
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
    }

    override fun registerProjectServices(registration: ServiceRegistration) {
    }

    object KotlinScriptBuildServices {

        @Suppress("unused")
        private fun createKotlinScriptClassPathProvider(
            classPathRegistry: ClassPathRegistry,
            dependencyFactory: DependencyFactory,
            jarCache: GeneratedGradleJarCache) =
            KotlinScriptClassPathProvider(classPathRegistry, dependencyFactory, versionedJarCacheFor(jarCache))

        private fun versionedJarCacheFor(jarCache: GeneratedGradleJarCache): JarCache =
            { id, creator -> jarCache["$id-$gradleScriptKotlinVersion", creator] }

        private val gradleScriptKotlinVersion by lazy {
            javaClass.`package`.implementationVersion
        }
    }
}
