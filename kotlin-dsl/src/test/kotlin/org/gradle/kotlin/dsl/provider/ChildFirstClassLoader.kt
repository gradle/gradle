/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.provider

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.ClassPath


/**
 * A [VisitableURLClassLoader] that tries to load classes locally before delegating to its parent.
 */
class ChildFirstClassLoader(parent: ClassLoader, classPath: ClassPath) : VisitableURLClassLoader("child-first", parent, classPath) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        findLoadedClass(name)
            ?: tryToLoadLocally(name)
            ?: super.loadClass(name, resolve)

    private
    fun tryToLoadLocally(name: String): Class<*>? =
        try {
            findClass(name)
        } catch (e: ClassNotFoundException) {
            null
        }
}
