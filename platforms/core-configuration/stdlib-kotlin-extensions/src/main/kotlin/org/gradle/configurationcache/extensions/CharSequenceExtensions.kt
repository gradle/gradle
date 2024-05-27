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

package org.gradle.configurationcache.extensions

import java.util.Locale


@Deprecated(
    "This was never intended as a public API.",
    ReplaceWith("this.let { if (it.isEmpty()) it else it[0].titlecase(java.util.Locale.getDefault()) + it.substring(1) }")
)
fun CharSequence.capitalized(): String =
    when {
        isEmpty() -> ""
        else -> get(0).let { initial ->
            when {
                initial.isLowerCase() -> initial.titlecase(Locale.getDefault()) + substring(1)
                else -> toString()
            }
        }
    }
