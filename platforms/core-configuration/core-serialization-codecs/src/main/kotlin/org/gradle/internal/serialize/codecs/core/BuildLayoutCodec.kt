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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.file.BuildLayout
import org.gradle.api.file.Directory
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import org.gradle.internal.serialize.graph.readNonNull


/**
 * Serializes [BuildLayout] by value.
 *
 * [BuildLayout] is a Settings-scoped service, but the data it exposes — the settings and root
 * directories — is immutable for the build. It can be captured in a settings script and used from a
 * task action, so it must survive the configuration cache. The general [org.gradle.internal.serialize.graph.codecs.ServicesCodec]
 * cannot help here: it re-resolves a service from the isolate owner's registry on load, and a task's
 * registry is Project-scoped, a sibling of the Settings scope, so the settings-scoped [BuildLayout]
 * is unreachable (and the Settings registry no longer exists at execution time). Instead, this codec
 * writes the two directories and reconstructs an immutable value on load.
 *
 * Must be bound ahead of [org.gradle.internal.serialize.graph.codecs.ServicesCodec] so that it wins
 * for this type (first matching binding is used).
 */
object BuildLayoutCodec : EncodingProducer, Decoding {

    override fun encodingForType(type: Class<*>): Encoding? =
        if (BuildLayout::class.java.isAssignableFrom(GeneratedSubclasses.unpack(type))) BuildLayoutEncoding else null

    override suspend fun ReadContext.decode(): BuildLayout {
        val settingsDirectory = readNonNull<Directory>()
        val rootDirectory = readNonNull<Directory>()
        return SerializedBuildLayout(settingsDirectory, rootDirectory)
    }
}


private object BuildLayoutEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        value as BuildLayout
        write(value.settingsDirectory)
        write(value.rootDirectory)
    }
}


private class SerializedBuildLayout(
    private val settingsDir: Directory,
    private val rootDir: Directory
) : BuildLayout {
    override fun getSettingsDirectory(): Directory = settingsDir
    override fun getRootDirectory(): Directory = rootDir
}
