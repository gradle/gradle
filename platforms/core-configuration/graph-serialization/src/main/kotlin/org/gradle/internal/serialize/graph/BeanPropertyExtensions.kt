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

package org.gradle.internal.serialize.graph

import org.gradle.api.GradleException
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.internal.configuration.problems.PropertyKind
import java.io.IOException
import java.lang.reflect.InvocationTargetException


/**
 * Writes a bean property.
 *
 * A property can only be written when there's a suitable [Codec] for its [value].
 */
suspend fun WriteContext.writePropertyValue(kind: PropertyKind, name: String, value: Any?) {
    withPropertyTrace(kind, name) {
        try {
            write(value)
        } catch (error: Exception) {
            onError(error) {
                when {
                    value != null -> {
                        text("error writing value of type ")
                        reference(GeneratedSubclasses.unpackType(value))
                    }

                    else -> {
                        text("error writing null value")
                    }
                }
            }
        }
    }
}


/**
 * Reads a bean property written with [writePropertyValue].
 */
@Suppress("ThrowsCount")
suspend fun ReadContext.readPropertyValue(kind: PropertyKind, name: String, action: (Any?) -> Unit) {
    withPropertyTrace(kind, name) {
        val value =
            try {
                read()
            } catch (passThrough: IOException) {
                throw passThrough
            } catch (passThrough: GradleException) {
                throw passThrough
            } catch (e: InvocationTargetException) {
                // unwrap ITEs as they are not useful to users
                throw GradleException("Could not load the value of $trace.", e.cause)
            } catch (e: Exception) {
                throw GradleException("Could not load the value of $trace.", e)
            }
        action(value)
    }
}
