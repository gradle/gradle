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

import groovy.json.JsonOutput.toJson

import org.gradle.api.internal.initialization.AbstractClassLoaderScope
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.internal.classloader.ClassLoaderVisitor

import org.gradle.kotlin.dsl.support.foldHierarchy

import java.net.URL

import java.util.*


/**
 * A formatter for strings that might contain file system paths.
 */
internal
typealias PathStringFormatter = (String) -> String


internal
fun classLoaderHierarchyJsonFor(
    klass: Class<*>,
    targetScope: ClassLoaderScope,
    pathFormatter: PathStringFormatter = { it }
) =

    classLoaderHierarchyJsonFor(
        hierarchyOf(klass.classLoader),
        hierarchyOf(targetScope),
        pathFormatter
    )


private
typealias ClassLoaderId = String


private
class ClassLoaderNode(
    val id: ClassLoaderId,
    val label: String,
    val classPath: MutableSet<URL> = LinkedHashSet(),
    val parents: MutableSet<ClassLoaderId> = LinkedHashSet()
)


private
fun classLoaderHierarchyJsonFor(
    classLoaders: List<ClassLoaderNode>,
    scopes: List<ClassLoaderScope>,
    pathFormatter: PathStringFormatter
): String {

    fun labelFor(scope: ClassLoaderScope) =
        pathFormatter(if (scope is AbstractClassLoaderScope) scope.path else scope.toString())

    return toJson(
        mapOf(
            "classLoaders" to classLoaders.map {
                mapOf(
                    "id" to it.id,
                    "label" to it.label,
                    "classPath" to it.classPath.map { pathFormatter(it.toString()) },
                    "parents" to it.parents
                )
            },
            "scopes" to scopes.map {
                mapOf(
                    "label" to labelFor(it),
                    "localClassLoader" to idOf(it.localClassLoader),
                    "exportClassLoader" to idOf(it.exportClassLoader),
                    "isLocked" to it.isLocked
                )
            }
        )
    )
}


private
fun hierarchyOf(initialScope: ClassLoaderScope): List<ClassLoaderScope> =
    initialScope.foldHierarchy(arrayListOf<ClassLoaderScope>()) { result, scope ->
        result.apply { add(scope) }
    }


private
fun hierarchyOf(classLoader: ClassLoader): ArrayList<ClassLoaderNode> {

    val classLoaders = arrayListOf<ClassLoaderNode>()
    val visitedClassLoaders = IdentityHashMap<ClassLoader, Boolean>()
    val stack = ArrayDeque<ClassLoaderNode>()
    val visitor = object : ClassLoaderVisitor() {
        override fun visit(classLoader: ClassLoader) {
            if (classLoader in visitedClassLoaders) {
                return
            }
            visitedClassLoaders.put(classLoader, true)

            val record = ClassLoaderNode(idOf(classLoader), classLoader.toString())
            classLoaders.add(record)

            stack.push(record)
            super.visit(classLoader)
            stack.pop()
        }

        override fun visitParent(classLoader: ClassLoader) {
            current.parents.add(idOf(classLoader))
            super.visitParent(classLoader)
        }

        override fun visitClassPath(classPath: Array<out URL?>) {
            current.classPath.addAll(classPath.filterNotNull())
        }

        private
        val current: ClassLoaderNode
            get() = stack.peek()!!
    }

    visitor.visit(classLoader)
    return classLoaders
}


private
fun idOf(classLoader: ClassLoader): String =
    "${classLoader::class.qualifiedName}@${System.identityHashCode(classLoader)}"
