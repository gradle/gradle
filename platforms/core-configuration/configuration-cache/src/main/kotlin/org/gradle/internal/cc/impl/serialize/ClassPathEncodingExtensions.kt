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

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.classpath.TransformedClassPath
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeFile


internal
fun WriteContext.writeClassPath(classPath: ClassPath) {
    // Ensure that the proper type is going to be restored,
    // because it is important for the equality checks.
    if (classPath is TransformedClassPath) {
        writeBoolean(true)
        writeTransformedClassPath(classPath)
    } else {
        writeBoolean(false)
        writeDefaultClassPath(classPath)
    }
}


private
fun WriteContext.writeDefaultClassPath(classPath: ClassPath) {
    writeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


private
fun WriteContext.writeTransformedClassPath(classPath: TransformedClassPath) {
    writeCollection(classPath.asFiles.zip(classPath.asTransformedFiles)) {
        writeFile(it.first)
        writeFile(it.second)
    }
}


internal
fun Decoder.readClassPath(): ClassPath {
    val isTransformed = readBoolean()
    return if (isTransformed) {
        readTransformedClassPath()
    } else {
        readDefaultClassPath()
    }
}


private
fun Decoder.readDefaultClassPath(): ClassPath {
    val size = readSmallInt()
    val builder = DefaultClassPath.builderWithExactSize(size)
    repeat(size) {
        builder.add(readFile())
    }
    return builder.build()
}


private
fun Decoder.readTransformedClassPath(): ClassPath {
    val size = readSmallInt()
    val builder = TransformedClassPath.builderWithExactSize(size)
    repeat(size) {
        builder.add(readFile(), readFile())
    }
    return builder.build()
}
