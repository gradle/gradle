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

package org.gradle.internal.serialize.codecs.stdlib

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.writeCollection


/**
 * Decodes HashSet instances as LinkedHashSet to preserve original iteration order.
 */
object HashSetCodec : Codec<HashSet<Any?>> {

    override suspend fun WriteContext.encode(value: HashSet<Any?>) {
        writeCollectionCheckingForCircularElements(value)
    }

    override suspend fun ReadContext.decode(): HashSet<Any?> =
        readCollectionCheckingForCircularElementsInto(::LinkedHashSet)
}


private
suspend fun WriteContext.writeCollectionCheckingForCircularElements(collection: Collection<Any?>) {
    var writeCircularMarker = true
    writeCollection(collection) { element ->
        if (element != null && element in circularReferences && element.javaClass.overridesHashCode()) {
            logPropertyProblem("serialize") {
                text("Circular references can lead to undefined behavior upon deserialization.")
            }
            if (writeCircularMarker) {
                write(CircularReferenceMarker.INSTANCE)
                writeCircularMarker = false
            }
        }
        write(element)
    }
}


private
suspend fun <T : MutableCollection<Any?>> ReadContext.readCollectionCheckingForCircularElementsInto(
    factory: (Int) -> T
): T {
    val size = readSmallInt()
    val container = factory(size)
    for (i in 0 until size) {
        val element = read()
        if (element === CircularReferenceMarker.INSTANCE) {
            // upon reading a circular reference, wait until all objects have been initialized
            // before inserting the elements in the resulting set.
            val remainingSize = size - i
            val remaining = ArrayList<Any?>(remainingSize).apply {
                for (j in 0 until remainingSize) {
                    add(read())
                }
            }
            onFinish {
                container.addAll(remaining)
            }
            return container
        }
        container.add(element)
    }
    return container
}


private
object CircularReferenceMarker {

    val INSTANCE: Any = Marker.INSTANCE

    private
    enum class Marker {
        INSTANCE
    }
}


private
fun Class<*>.overridesHashCode(): Boolean =
    getMethod("hashCode").declaringClass !== java.lang.Object::class.java
