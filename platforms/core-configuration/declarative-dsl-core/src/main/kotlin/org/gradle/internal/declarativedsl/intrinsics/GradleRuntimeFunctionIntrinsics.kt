/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.intrinsics

import kotlin.reflect.jvm.javaMethod

val gradleRuntimeIntrinsicsKClass = ::self.javaMethod!!.declaringClass.kotlin

/**
 * Intrinsic bridge for invoking [kotlin.collections.listOf], which cannot be invoked via reflection due to its containing class not being public.
 */
@IntrinsicTopLevelFunctionBridge("kotlin.collections.listOf")
fun <T> listOf(vararg elements: T): List<T> = kotlin.collections.listOf(*elements)

// Used just to get the reference to the containing class, as the other functions here are generic and cannot easily be referenced
private fun self(): Nothing = error("should never be invoked")
