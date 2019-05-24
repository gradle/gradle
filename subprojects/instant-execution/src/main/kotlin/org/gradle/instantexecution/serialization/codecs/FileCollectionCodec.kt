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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.serialize.SetSerializer
import java.io.File


internal
class FileCollectionCodec(
    private val fileSetSerializer: SetSerializer<File>,
    private val fileCollectionFactory: FileCollectionFactory
) : Codec<FileCollection> {

    override fun WriteContext.encode(value: FileCollection) =
        fileSetSerializer.write(this, value.files)

    override fun ReadContext.decode(): FileCollection? =
        fileCollectionFactory.fixed(fileSetSerializer.read(this))
}
