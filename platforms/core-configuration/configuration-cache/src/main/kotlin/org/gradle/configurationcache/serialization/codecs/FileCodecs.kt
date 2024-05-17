/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.FileFactory
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.writeFile


class DirectoryCodec(private val fileFactory: FileFactory) : Codec<Directory> {
    override suspend fun WriteContext.encode(value: Directory) {
        writeFile(value.asFile)
    }

    override suspend fun ReadContext.decode(): Directory {
        return fileFactory.dir(readFile())
    }
}


class RegularFileCodec(private val fileFactory: FileFactory) : Codec<RegularFile> {
    override suspend fun WriteContext.encode(value: RegularFile) {
        writeFile(value.asFile)
    }

    override suspend fun ReadContext.decode(): RegularFile {
        return fileFactory.file(readFile())
    }
}
