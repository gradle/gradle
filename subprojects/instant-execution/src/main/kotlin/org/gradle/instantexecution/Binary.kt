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

package org.gradle.instantexecution

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


interface WriteContext : Encoder


interface ReadContext : Decoder {

    val taskClassLoader: ClassLoader

    fun getProject(path: String): ProjectInternal
}


internal
class DefaultWriteContext(

    private
    val encoder: Encoder

) : WriteContext, Encoder by encoder {

    // TODO: consider interning strings
    override fun writeString(string: CharSequence) =
        encoder.writeString(string)
}


internal
class DefaultReadContext(

    private
    val decoder: Decoder

) : ReadContext, Decoder by decoder {

    private
    lateinit var projectProvider: ProjectProvider

    private
    lateinit var classLoader: ClassLoader

    internal
    fun initialize(projectProvider: ProjectProvider, classLoader: ClassLoader) {
        this.projectProvider = projectProvider
        this.classLoader = classLoader
    }

    override val taskClassLoader: ClassLoader
        get() = classLoader

    override fun getProject(path: String): ProjectInternal =
        projectProvider(path)
}


internal
typealias ProjectProvider = (String) -> ProjectInternal
