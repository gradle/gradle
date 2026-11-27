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

import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionCandidatesProvider
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

/**
 * Annotates a bridge function needed to invoke top-level functions that are otherwise non-callable via reflection, such as the top-level functions in kotlin-stdlib.
 *
 * For instance, `kotlin.collections.CollectionsKt` inherits its functions from non-public multi-file fragment classes like `kotlin.collections.CollectionsKt_CollectionsJvmKt`.
 * The functions inherited that way cannot be called via reflection.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IntrinsicTopLevelFunctionBridge(val topLevelFunctionFqn: String)

/**
 * Resolves calls to top-level functions identified by [IntrinsicTopLevelFunctionBridge.topLevelFunctionFqn].
 * Bridges those calls to the corresponding annotated functions.
 */
class IntrinsicRuntimeFunctionCandidatesProvider(intrinsicImplementationClasses: List<KClass<*>>) : RuntimeFunctionCandidatesProvider {
    private val intrinsicsByFqn = intrinsicImplementationClasses.flatMap { clazz ->
        clazz.java.methods.mapNotNull { javaMethod ->
            var bridgeAnnotation = javaMethod.getAnnotation(IntrinsicTopLevelFunctionBridge::class.java)
            if (bridgeAnnotation == null || !Modifier.isStatic(javaMethod.modifiers))
                 null
            else bridgeAnnotation.topLevelFunctionFqn to checkNotNull(javaMethod.kotlinFunction) { "expected kotlinFunction to be present for Java method $javaMethod" }
        }
    }.groupBy({ (fqn, _) -> fqn }, valueTransform = { (_, kFunction) -> kFunction })

    override fun candidatesForTopLevelFunction(
        schemaFunction: DataTopLevelFunction,
        scopeClassLoader: ClassLoader
    ): List<KFunction<*>> {
        val fqn = schemaFunction.packageName.takeIf { it.isNotEmpty() }?.let { "$it." }.orEmpty() + schemaFunction.simpleName
        return intrinsicsByFqn[fqn].orEmpty()
    }
}
