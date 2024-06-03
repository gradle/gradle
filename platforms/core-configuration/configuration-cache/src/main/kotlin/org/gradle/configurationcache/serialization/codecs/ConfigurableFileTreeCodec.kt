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

import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeFile


internal
class ConfigurableFileTreeCodec(
    private val fileCollectionFactory: FileCollectionFactory
) : Codec<ConfigurableFileTree> {
    override suspend fun WriteContext.encode(value: ConfigurableFileTree) {
        writeFile(value.dir)
        write(value.patterns)
    }

    override suspend fun ReadContext.decode(): ConfigurableFileTree {
        val dir = readFile()
        val patterns = read() as PatternSet
        val tree = fileCollectionFactory.fileTree()
        tree.setDir(dir)
        // TODO - read patterns directly into tree
        tree.patterns.copyFrom(patterns)
        return tree
    }
}
