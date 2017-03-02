/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.script.lang.kotlin.support

import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import java.io.File

import java.net.URLClassLoader


internal
fun exportClassPathFromHierarchyOf(scope: ClassLoaderScope): ClassPath =
    scope.foldHierarchy(initial = DefaultClassPath.EMPTY) { classPath, scope ->
        classPath + exportClassPathOf(scope)
    }


private
fun exportClassPathOf(baseScope: ClassLoaderScope): ClassPath =
    DefaultClassPath.of(
        (baseScope.exportClassLoader as? URLClassLoader)
            ?.urLs
            ?.map { File(it.toURI()) })


internal inline
fun <T> ClassLoaderScope.foldHierarchy(initial: T, operation: (T, ClassLoaderScope) -> T): T {
    var value = initial
    traverseHierarchy { value = operation(value, it) }
    return value
}


internal inline
fun ClassLoaderScope.traverseHierarchy(action: (ClassLoaderScope) -> Unit) {
    action(this)
    traverseAncestors(action)
}


internal inline
fun ClassLoaderScope.traverseAncestors(action: (ClassLoaderScope) -> Unit) {
    var scope = this
    while (scope.parent != scope) {
        scope = scope.parent
        action(scope)
    }
}
