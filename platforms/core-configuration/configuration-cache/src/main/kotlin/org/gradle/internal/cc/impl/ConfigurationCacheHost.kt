/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.Factory
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.service.scopes.Scope.Build
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File

@ServiceScope(Build::class)
interface ConfigurationCacheHost : HostServiceProvider {

    val currentBuild: VintageGradleBuild

    fun createBuild(settingsFile: File?): ConfigurationCacheBuild

    fun visitBuilds(visitor: (VintageGradleBuild) -> Unit)

    fun <T> factory(serviceType: Class<T>): Factory<T>
}
