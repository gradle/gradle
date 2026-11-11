/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core.jos

import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.withBeanTrace
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier


/**
 * Reads the class of the bean, prepares its instance with the given `id` and BeanStateReader for the type,
 * and then invokes the provided decoding callback. Useful to cross from Configuration Cache serialization to Java serialization.
 */
internal inline fun ReadContext.decodingBeanWithId(id: Int, decode: (Any, Class<*>, BeanStateReader) -> Unit): Any {
    val beanType = readClass()
    return withBeanTrace(beanType) {
        val beanStateReader = beanStateReaderFor(beanType)
        beanStateReader.run { newBeanWithId(id) }.also { bean ->
            decode(bean, beanType, beanStateReader)
        }
    }
}


/**
 * Queries for Java Serialization methods declared in the class.
 */
@JvmInline
internal value class SerializationMethods private constructor(private val methods: List<Method>) {
    /**
     * A `writeReplace` method conforming to Java Serialization Spec or `null` if there is none.
     */
    val writeReplace: Method?
        get() = methods.find { it.isWriteReplace() }

    /**
     * A `readResolve` method conforming to Java Serialization Spec or `null` if there is none.
     */
    val readResolve: Method?
        get() = methods.find { it.isReadResolve() }

    /**
     * A list of `writeObject(ObjectOutputStream)` methods declared in this class and its superclasses, starting with the most distant ancestor.
     */
    val writeObjectMethodHierarchy: List<Method>
        get() = methods.serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)

    /**
     * A list of `readObject(ObjectInputStream)` methods declared in this class and its superclasses, starting with the most distant ancestor.
     */
    val readObjectMethodHierarchy: List<Method>
        get() = methods.serializationMethodHierarchy("readObject", ObjectInputStream::class.java)

    companion object {
        fun of(cls: Class<*>) = SerializationMethods(ClassInspector.inspectMethods(cls))
    }
}

/**
 * Is this method a `writeReplace()` method conforming to the Java Serialization Spec?
 */
private fun Method.isWriteReplace() =
    !Modifier.isStatic(modifiers)
        && parameterCount == 0
        && returnType == Any::class.java
        && name == "writeReplace"


/**
 * Adapts a method handle to the `writeReplace` signature.
 */
internal fun MethodHandle.asWriteReplace(): MethodHandle = asType(methodType(Any::class.java, Any::class.java))


/**
 * Is this method a `readResolve()` method conforming to the Java Serialization Spec?
 */
private fun Method.isReadResolve() =
    !Modifier.isStatic(modifiers)
        && parameterCount == 0
        && returnType == Any::class.java
        && name == "readResolve"


/**
 * Adapts a method handle to the `readResolve` signature.
 */
internal fun MethodHandle.asReadResolve(): MethodHandle = asType(methodType(Any::class.java, Any::class.java))


/**
 * Returns the private serialization methods with the given name of the type hierarchy, starting from the topmost class.
 */
private fun List<Method>.serializationMethodHierarchy(methodName: String, streamType: Class<*>): List<Method> =
    filter { method ->
        method.run {
            Modifier.isPrivate(modifiers)
                && parameterCount == 1
                && returnType == Void.TYPE
                && name == methodName
                && parameterTypes[0].isAssignableFrom(streamType)
        }
    }.asReversed()


/**
 * Adapts a method handle to the `writeObject` signature.
 */
internal fun MethodHandle.asWriteObject(): MethodHandle = asType(methodType(Void.TYPE, Any::class.java, ObjectOutputStream::class.java))


/**
 * Adapts a method handle to the `readObject` signature.
 */
internal fun MethodHandle.asReadObject(): MethodHandle = asType(methodType(Void.TYPE, Any::class.java, ObjectInputStream::class.java))
