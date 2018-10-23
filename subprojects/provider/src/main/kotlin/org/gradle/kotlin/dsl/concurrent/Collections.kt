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

package org.gradle.kotlin.dsl.concurrent

import org.gradle.api.JavaVersion

import kotlin.streams.asSequence
import kotlin.streams.asStream


// TODO:accessors honor Gradle max workers?
internal
fun <T, U> Sequence<T>.unorderedParallelMap(f: (T) -> U): Sequence<U> =
    if (JavaVersion.current().isJava8) map(f) // Java 8 stream API impl is too resource hungry
    else asStream().unordered().parallel().map(f).asSequence()
