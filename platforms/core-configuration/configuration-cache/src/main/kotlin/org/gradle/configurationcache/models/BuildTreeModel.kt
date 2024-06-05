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

package org.gradle.configurationcache.models

import org.gradle.internal.extensions.stdlib.uncheckedCast


/**
 * Model built by a build action supplied via Tooling API
 */
sealed class BuildTreeModel {

    abstract fun <T> result(): T?

    object NullModel : BuildTreeModel() {
        override fun <T> result(): T? = null
    }

    class Model(val value: Any) : BuildTreeModel() {
        override fun <T> result(): T? = value.uncheckedCast()
    }
}
