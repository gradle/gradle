/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.services

import org.gradle.initialization.Environment
import org.gradle.internal.extensions.stdlib.filterKeysByPrefix
import org.gradle.internal.extensions.stdlib.uncheckedCast


/**
 * Gives direct access to system resources.
 */
open class DefaultEnvironment : Environment {

    override fun getSystemProperties(): Environment.Properties =
        DefaultProperties(System.getProperties().uncheckedCast())

    override fun getVariables(): Environment.Properties =
        DefaultProperties(System.getenv())

    internal
    open class DefaultProperties(val map: Map<String, String>) : Environment.Properties {
        override fun byNamePrefix(prefix: String): Map<String, String> =
            map.filterKeysByPrefix(prefix)

        override fun get(name: String): String? =
            map[name]
    }
}
