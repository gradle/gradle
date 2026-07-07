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

import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.ScriptFileOperations
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.writeFile
import org.gradle.internal.service.ServiceRegistry


/**
 * Serializes a [ScriptFileOperations] by storing only its base directory and rebuilding an
 * equivalent instance from the owning build's services on load.
 *
 * Ordinary [org.gradle.api.internal.file.FileOperations] are re-resolved from the isolate owner by
 * `ServicesCodec`, which yields the owner's own file operations — rooted at the project directory,
 * not at the script's directory. That silently changes what a script's `file(...)` resolves against
 * once the build is served from the configuration cache. A [ScriptFileOperations] carries its base
 * directory explicitly, so we can round-trip it faithfully: [DefaultFileOperations.forScript]
 * rebuilds a file operations rooted at the same directory from the owner's [ServiceRegistry].
 *
 * This codec must be bound before `ServicesCodec`, which would otherwise claim these instances via
 * the `@ServiceScope` on [org.gradle.api.internal.file.FileOperations].
 *
 * See [#22879](https://github.com/gradle/gradle/issues/22879).
 */
object ScriptFileOperationsCodec : Codec<ScriptFileOperations> {

    override suspend fun WriteContext.encode(value: ScriptFileOperations) {
        writeFile(value.baseDir)
    }

    override suspend fun ReadContext.decode(): ScriptFileOperations {
        val baseDir = readFile()
        val services = isolate.owner.serviceOf<ServiceRegistry>()
        return DefaultFileOperations.forScript(services, baseDir)
    }
}
