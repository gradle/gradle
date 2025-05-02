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

package org.gradle.internal.cc.impl.serialize


internal
val gradleRuntimeClassLoader = DefaultClassEncoder::class.java.classLoader


internal
fun classForName(name: String, classLoader: ClassLoader): Class<*> =
    try {
        Class.forName(name, false, classLoader)
    } catch (e: ClassNotFoundException) {
        throw ClassNotFoundException("Class '$name' not found in ${describeClassLoader(classLoader)}.").apply {
            addSuppressed(e)
        }
    }


internal
fun describeClassLoader(classLoader: ClassLoader): String =
    if (classLoader === gradleRuntimeClassLoader)
        "Gradle runtime ${classLoaderString(gradleRuntimeClassLoader)}"
    else
        classLoaderString(classLoader)


private
fun classLoaderString(classLoader: ClassLoader) =
    "class loader '$classLoader' of type '${classLoader.javaClass.name}'"
