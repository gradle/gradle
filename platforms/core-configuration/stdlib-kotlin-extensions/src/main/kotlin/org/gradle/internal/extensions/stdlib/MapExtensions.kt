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

package org.gradle.internal.extensions.stdlib


fun <V> Map<String, V>.filterKeysByPrefix(prefix: String): Map<String, V?> =
    filterKeys { key -> key.length > prefix.length && key.startsWith(prefix) }


/**
 * Inverts the given map by swapping its keys with their corresponding values and returns the resulting map.
 *
 * If the original map contains duplicate values, the resulting map will map each value to the key associated
 * with the last occurrence of that value in the original map's iteration order.
 */
fun <K, V> Map<K, V>.invert() = HashMap<V, K>(size).also { result ->
    forEach { (k, v) ->
        result[v] = k
    }
}
