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

import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.extensions.stdlib.unsafeLazy
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Identifies a specific configuration cache entry on disk. The [string] digest doubles
 * as the entry's directory name; equality and `hashCode` are defined by it, so two
 * instances built from identical start parameters compare equal.
 * <p>
 * Composes [ConfigurationCacheEnvironmentKey] (every component except the requested
 * task names) and appends [ConfigurationCacheStartParameter.requestedTaskNames] when
 * [BuildActionModelRequirements.isRunsTasks] is true.
 */
@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheKey(
    private val environmentKey: ConfigurationCacheEnvironmentKey,
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildActionRequirements: BuildActionModelRequirements
) {

    val string: String by unsafeLazy {
        Hashing.md5().newHasher().apply {
            environmentKey.appendComponents(this)
            if (buildActionRequirements.isRunsTasks) {
                appendRequestedTasks()
            }
        }.hash().toCompactString()
    }

    override fun toString() = string

    override fun hashCode(): Int = string.hashCode()

    override fun equals(other: Any?): Boolean = (other as? ConfigurationCacheKey)?.string == string

    private
    fun Hasher.appendRequestedTasks() {
        val names = startParameter.requestedTaskNames
        putInt(names.size)
        names.forEach(::putString)
    }
}
