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

import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.resources.ApiTextResourceAdapter
import org.gradle.api.resources.TextResourceFactory
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService
import java.net.URI


object ApiTextResourceAdapterCodec : Codec<ApiTextResourceAdapter> {

    override suspend fun WriteContext.encode(value: ApiTextResourceAdapter) {
        writeString((value.inputProperties as URI).toASCIIString())
    }

    override suspend fun ReadContext.decode(): ApiTextResourceAdapter =
        textResourceFactory.fromInsecureUri(readString()) as ApiTextResourceAdapter

    private
    val ReadContext.textResourceFactory: TextResourceFactory
        get() = ownerService<FileOperations>().resources.text
}
