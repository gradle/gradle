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

package org.gradle.script.lang.kotlin.provider

import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.internal.classloader.ClassLoaderVisitor

import java.io.ByteArrayOutputStream
import java.io.PrintStream

import java.net.URL

import java.util.*

fun classLoaderHierarchyJsonFor(klass: Class<*>, targetScope: ClassLoaderScope): String {
    val outputStream = ByteArrayOutputStream()
    writeClassLoaderHierarchyJsonTo(outputStream.let(::PrintStream), klass, targetScope)
    return outputStream.toString()
}

/**
 * A formatter for strings that might contain file system paths.
 */
typealias PathStringFormatter = (String) -> String

fun writeClassLoaderHierarchyJsonTo(writer: PrintStream,
                                    klass: Class<*>,
                                    targetScope: ClassLoaderScope,
                                    pathFormatter: PathStringFormatter = { it }) {
    writeClassLoaderHierarchyJsonTo(
        writer,
        hierarchyOf(klass.classLoader),
        hierarchyOf(targetScope),
        pathFormatter)
}

private
typealias ClassLoaderId = String

private
class ClassLoaderNode(
    val id: ClassLoaderId,
    val label: String,
    val classPath: MutableSet<URL> = LinkedHashSet(),
    val parents: MutableSet<ClassLoaderId> = LinkedHashSet())


private
fun writeClassLoaderHierarchyJsonTo(writer: PrintStream,
                                    classLoaders: List<ClassLoaderNode>,
                                    scopes: List<ClassLoaderScope>,
                                    pathFormatter: PathStringFormatter) {
    writer.run {
        println("{")
        println("\"classLoaders\": [")
        for ((i, record) in classLoaders.withIndex()) {
            if (i > 0) println(",")
            print("""
                { "id": "${record.id}"
                , "label": "${record.label}"
                , "classPath": ${toJsonArray(record.classPath.map { pathFormatter(it.toString()) })}
                , "parents": ${toJsonArray(record.parents)}
                }
            """.replaceIndent("  "))
        }
        println()
        println("],")
        println("\"scopes\": [")
        for ((i, scope) in scopes.withIndex()) {
            if (i > 0) println(",")
            print("""
                { "label": "${pathFormatter(scope.toString())}"
                , "localClassLoader": "${idOf(scope.localClassLoader)}"
                , "exportClassLoader": "${idOf(scope.exportClassLoader)}"
                , "isLocked": "${scope.isLocked}"
                }
            """.replaceIndent("  "))
        }
        println("]")
        println("}")
    }
}

private
fun toJsonArray(array: Collection<*>) =
    array.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

private
fun hierarchyOf(initialScope: ClassLoaderScope): List<ClassLoaderScope> {
    val result = arrayListOf(initialScope)
    var scope = initialScope
    while (scope.parent != scope) {
        result.add(scope.parent)
        scope = scope.parent
    }
    return result
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

        private val current: ClassLoaderNode
            get() = stack.peek()!!
    }

    visitor.visit(classLoader)
    return classLoaders
}

private
fun idOf(classLoader: ClassLoader): String =
    "${classLoader.javaClass.name}@${System.identityHashCode(classLoader)}"
