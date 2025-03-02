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

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.IntersectionPatternSet
import org.gradle.internal.Factory
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readStrings
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeStrings


class PatternSetCodec(private val patternSetFactory: Factory<PatternSet>) : Codec<PatternSet> {

    override suspend fun WriteContext.encode(value: PatternSet) {
        writePatternSet(value)
    }

    override suspend fun ReadContext.decode() =
        patternSetFactory.create()!!.apply {
            readPatternSet(this)
        }
}


object IntersectionPatternSetCodec : Codec<IntersectionPatternSet> {

    override suspend fun WriteContext.encode(value: IntersectionPatternSet) {
        write(value.other)
        writePatternSet(value)
    }

    override suspend fun ReadContext.decode(): IntersectionPatternSet {
        val other = read() as PatternSet
        return IntersectionPatternSet(other).apply {
            readPatternSet(this)
        }
    }
}


private
suspend fun WriteContext.writePatternSet(value: PatternSet) {
    writeBoolean(value.isCaseSensitive)
    writeStrings(value.includesView)
    writeStrings(value.excludesView)
    writeCollection(value.includeSpecsView)
    writeCollection(value.excludeSpecsView)
}


private
suspend fun ReadContext.readPatternSet(value: PatternSet) {
    value.isCaseSensitive = readBoolean()
    value.setIncludes(readStrings())
    value.setExcludes(readStrings())
    readCollection {
        value.include(readNonNull<Spec<FileTreeElement>>())
    }
    readCollection {
        value.exclude(readNonNull<Spec<FileTreeElement>>())
    }
}
