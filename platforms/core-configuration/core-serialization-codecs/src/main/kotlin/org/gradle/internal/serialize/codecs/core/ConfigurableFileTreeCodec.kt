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

import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeFile


class ConfigurableFileTreeCodec(
    private val fileCollectionFactory: FileCollectionFactory
) : Codec<ConfigurableFileTree> {
    override suspend fun WriteContext.encode(value: ConfigurableFileTree) {
        require(value is DefaultConfigurableFileTree)
        write(value.resolver)
        writeFile(value.dir)
        write(value.patterns)
    }

    override suspend fun ReadContext.decode(): ConfigurableFileTree {
        val resolver = readNonNull<PathToFileResolver>()
        val dir = readFile()
        val patterns = readNonNull<PatternSet>()
        val tree = fileCollectionFactory.withResolver(resolver).fileTree()
        tree.setDir(dir)
        // TODO - read patterns directly into tree
        tree.patterns.copyFrom(patterns)
        return tree
    }
}
