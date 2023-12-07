/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.models

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.tooling.internal.consumer.connection.ToolingParameterProxy
import org.gradle.tooling.provider.model.internal.ToolingModelParameterHasher


class DefaultToolingModelParameterHasher(
    private val valueSnapshotter: ValueSnapshotter
) : ToolingModelParameterHasher {

    override fun hash(parameter: Any): HashCode {
        val unpacked = ToolingParameterProxy.unpackProperties(parameter)
        val hasher: Hasher = Hashing.newHasher()
        hasher.put(valueSnapshotter.snapshot(unpacked))
        return hasher.hash()
    }

}
