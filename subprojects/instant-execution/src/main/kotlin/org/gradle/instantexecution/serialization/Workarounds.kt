/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization

import org.gradle.api.internal.initialization.ClassLoaderScope
import java.lang.reflect.Field
import java.util.concurrent.ForkJoinPool


internal
object Workarounds {

    private
    val ignoredBeanFields = listOf(
        // Ignore a lambda field for now
        "mFolderFilter" to "com.android.ide.common.resources.DataSet"
    )

    fun isIgnoredBeanField(field: Field) =
        ignoredBeanFields.contains(field.name to field.declaringClass.name)

    private
    val staticFieldsByTypeName = mapOf(
        "com.android.build.gradle.internal.tasks.Workers" to mapOf(
            "aapt2ThreadPool" to { ForkJoinPool(8) }
        )
    )

    fun maybeSetDefaultStaticStateIn(scope: ClassLoaderScope) {
        listOf(scope.localClassLoader, scope.exportClassLoader).forEach { loader ->
            staticFieldsByTypeName.forEach { (type, fields) ->
                try {
                    val clazz = loader.loadClass(type)
                    fields.forEach { (name, value) ->
                        try {
                            clazz.getDeclaredField(name)
                                .apply { isAccessible = true }
                                .takeIf { it.get(null) == null }
                                ?.set(null, value())
                        } catch (ex: NoSuchFieldException) {
                            // n/a
                        }
                    }
                } catch (ex: ClassNotFoundException) {
                    // n/a
                }
            }
        }
    }
}
