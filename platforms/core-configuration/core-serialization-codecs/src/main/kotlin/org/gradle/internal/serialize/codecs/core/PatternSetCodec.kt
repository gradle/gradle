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
import org.gradle.api.internal.provider.LazyBackedPatternSet
import org.gradle.api.provider.Property
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.FilePatterns
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.DefaultFilePatterns
import org.gradle.api.tasks.util.internal.IntersectionPatternSet
import org.gradle.api.tasks.util.internal.PatternSetFactory
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readStrings
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeStrings


class PatternSetCodec(private val patternSetFactory: PatternSetFactory) : Codec<PatternSet> {

    override suspend fun WriteContext.encode(value: PatternSet) {
        writePatternSet(value)
    }

    override suspend fun ReadContext.decode(): PatternSet =
        patternSetFactory.createPatternSet().apply {
            readPatternSet(this)
        }
}


/**
 * Writes the property, not its value, so a value bound to a provider stays lazy.
 */
class LazyBackedPatternSetCodec(private val patternSetFactory: PatternSetFactory) : Codec<LazyBackedPatternSet> {

    override suspend fun WriteContext.encode(value: LazyBackedPatternSet) {
        encodePreservingIdentityOf(value) {
            write(value.property)
        }
    }

    override suspend fun ReadContext.decode(): LazyBackedPatternSet =
        decodePreservingIdentity { id ->
            val property = readNonNull<Property<FilePatterns>>()
            val patternSet = LazyBackedPatternSet(patternSetFactory.createPatternSet(), property)
            isolate.identities.putInstance(id, patternSet)
            patternSet
        }
}


object FilePatternsCodec : Codec<DefaultFilePatterns> {

    override suspend fun WriteContext.encode(value: DefaultFilePatterns) {
        writeBoolean(value.isCaseSensitive)
        writeStrings(value.includes)
        writeStrings(value.excludes)
        writeCollection(value.includeSpecs)
        writeCollection(value.excludeSpecs)
    }

    override suspend fun ReadContext.decode(): DefaultFilePatterns {
        val caseSensitive = readBoolean()
        val includes = readStrings()
        val excludes = readStrings()
        val includeSpecs = mutableListOf<Spec<FileTreeElement>>()
        readCollection { includeSpecs.add(readNonNull()) }
        val excludeSpecs = mutableListOf<Spec<FileTreeElement>>()
        readCollection { excludeSpecs.add(readNonNull()) }
        return DefaultFilePatterns(includes, excludes, includeSpecs, excludeSpecs, caseSensitive)
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
