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
package org.gradle.kotlin.dsl

import org.gradle.api.file.ContentFilterable

import java.io.FilterReader
import kotlin.reflect.KClass


/**
 * Adds a content filter to be used during the copy.
 * Multiple calls add additional filters to the filter chain.
 * Each filter should implement [FilterReader].
 * Import `org.apache.tools.ant.filters.*` for access to all the standard Ant filters.
 *
 * Examples:
 *
 * ```
 * filter<StripJavaComments>()
 * filter<com.mycompany.project.CustomFilter>()
 * filter<HeadFilter>("lines" to 25, "skip" to 2)
 * filter<ReplaceTokens>("tokens" to mapOf("copyright" to "2009", "version" to "2.3.1"))
 * ```
 *
 * @param T type of the filter to add
 * @param properties map of filter properties
 * @return this
 */
inline fun <reified T : FilterReader> ContentFilterable.filter(vararg properties: Pair<String, Any?>) =
    if (properties.isEmpty()) filter(T::class.java)
    else filter(mapOf(*properties), T::class.java)


/**
 * Adds a content filter to be used during the copy.
 * Multiple calls add additional filters to the filter chain.
 * Each filter should implement [FilterReader].
 * Import `org.apache.tools.ant.filters.*` for access to all the standard Ant filters.
 *
 * Examples:
 *
 * ```
 * filter<HeadFilter>(mapOf("lines" to 25, "skip" to 2))
 * filter<ReplaceTokens>(mapOf("tokens" to mapOf("copyright" to "2009", "version" to "2.3.1")))
 * ```
 *
 * @param T type of the filter to add
 * @param properties map of filter properties
 * @return this
 */
inline fun <reified T : FilterReader> ContentFilterable.filter(properties: Map<String, Any?>) =
    if (properties.isEmpty()) filter(T::class.java)
    else filter(properties, T::class.java)


/**
 * Adds a content filter to be used during the copy.
 * Multiple calls add additional filters to the filter chain.
 * Each filter should implement [FilterReader].
 * Import `org.apache.tools.ant.filters.*` for access to all the standard Ant filters.
 *
 * Examples:
 *
 * ```
 * filter(StripJavaComments::class)
 * filter(com.mycompany.project.CustomFilter::class)
 * filter(HeadFilter::class, "lines" to 25, "skip" to 2)
 * filter(ReplaceTokens::class, "tokens" to mapOf("copyright" to "2009", "version" to "2.3.1"))
 * ```
 *
 * @param filterType type of the filter to add
 * @param properties map of filter properties
 * @return this
 */
fun <T : FilterReader> ContentFilterable.filter(filterType: KClass<T>, vararg properties: Pair<String, Any?>) =
    if (properties.isEmpty()) filter(filterType.java)
    else filter(mapOf(*properties), filterType.java)


/**
 * Adds a content filter to be used during the copy.
 * Multiple calls add additional filters to the filter chain.
 * Each filter should implement [FilterReader].
 * Import `org.apache.tools.ant.filters.*` for access to all the standard Ant filters.
 *
 * Examples:
 *
 * ```
 * filter(HeadFilter::class, mapOf("lines" to 25, "skip" to 2))
 * filter(ReplaceTokens::class, mapOf("tokens" to mapOf("copyright" to "2009", "version" to "2.3.1")))
 * ```
 *
 * @param filterType type of the filter to add
 * @param properties map of filter properties
 * @return this
 */
fun <T : FilterReader> ContentFilterable.filter(filterType: KClass<T>, properties: Map<String, Any?>) =
    if (properties.isEmpty()) filter(filterType.java)
    else filter(properties, filterType.java)
