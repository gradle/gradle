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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull


class DestinationRootCopySpecCodec(
    private val fileResolver: FileResolver,
    private val objectFactory: ObjectFactory
) : Codec<DestinationRootCopySpec> {

    override suspend fun WriteContext.encode(value: DestinationRootCopySpec) {
        write(value.destinationDir)
        write(value.delegate)
    }

    override suspend fun ReadContext.decode(): DestinationRootCopySpec {
        val destDir = readNonNull<DirectoryProperty>()
        val delegate = read() as CopySpecInternal
        val spec = objectFactory.newInstance(DestinationRootCopySpec::class.java, fileResolver, objectFactory, delegate)
        spec.destinationDir.set(destDir)
        return spec
    }
}
