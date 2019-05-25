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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.reflect.Instantiator
import java.io.File


internal
class DefaultCopySpecCodec(
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator
) : Codec<DefaultCopySpec> {

    override fun WriteContext.encode(value: DefaultCopySpec) {
        val allSourcePaths = ArrayList<File>()
        collectSourcePathsFrom(value, allSourcePaths)
        write(allSourcePaths)
    }

    override fun ReadContext.decode(): DefaultCopySpec {
        @Suppress("unchecked_cast")
        val sourceFiles = read() as List<File>
        val copySpec = DefaultCopySpec(fileResolver, instantiator)
        copySpec.from(sourceFiles)
        return copySpec
    }

    private
    fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
        files.addAll(copySpec.resolveSourceFiles())
        for (child in copySpec.children) {
            collectSourcePathsFrom(child as DefaultCopySpec, files)
        }
    }
}
