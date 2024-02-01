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

package org.gradle.internal.declarativedsl.project

import org.gradle.internal.declarativedsl.schemaBuilder.CompositeConfigureLambdas
import org.gradle.internal.declarativedsl.schemaBuilder.MemberFilter
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions


internal
class ThirdPartyTypesDiscovery(
    private val memberFilter: MemberFilter,
    private val configureLambdas: CompositeConfigureLambdas,
) : TypeDiscovery {
    /**
     * Collect everything that potentially looks like types configured by the lambdas.
     * TODO: this may be excessive
     */
    override fun getClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> =
        kClass.memberFunctions
            .filter { memberFilter.shouldIncludeMember(it) }
            .mapNotNullTo(mutableSetOf()) { fn -> fn.parameters.lastOrNull()?.let { configureLambdas.getTypeConfiguredByLambda(it.type)?.classifier as? KClass<*> } }
}


internal
class CachedHierarchyAnnotationChecker(private val annotationType: KClass<out Annotation>) {
    fun isAnnotatedMaybeInSupertypes(kClass: KClass<*>): Boolean {
        // Can't use computeIfAbsent because of recursive calls
        hasRestrictedAnnotationWithSuperTypesCache[kClass]?.let { return it }
        return hasRestrictedAnnotationWithSuperTypes(kClass).also { hasRestrictedAnnotationCache[kClass] = it }
    }

    private
    fun hasRestrictedAnnotationWithSuperTypes(kClass: KClass<*>) =
        hasRestrictedAnnotationCached(kClass) || kClass.supertypes.any { (it.classifier as? KClass<*>)?.let { isAnnotatedMaybeInSupertypes(it) } ?: false }

    private
    val hasRestrictedAnnotationWithSuperTypesCache = mutableMapOf<KClass<*>, Boolean>()

    private
    val hasRestrictedAnnotationCache = mutableMapOf<KClass<*>, Boolean>()

    private
    fun hasRestrictedAnnotationCached(kClass: KClass<*>) = hasRestrictedAnnotationCache.computeIfAbsent(kClass) { hasAnnotation(it) }

    private
    fun hasAnnotation(kClass: KClass<*>) = kClass.annotations.any { annotationType.isInstance(it) }
}
