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

import org.gradle.api.file.ReadOnlyFileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.IntersectionPatternSet
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.readStrings
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeStrings
import org.gradle.internal.Factory


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
    writeStrings(value.includes)
    writeStrings(value.excludes)
    writeCollection(value.includeSpecs)
    writeCollection(value.excludeSpecs)
}


private
suspend fun ReadContext.readPatternSet(value: PatternSet) {
    value.isCaseSensitive = readBoolean()
    value.setIncludes(readStrings())
    value.setExcludes(readStrings())
    readCollection {
        value.include(readNonNull<Spec<ReadOnlyFileTreeElement>>())
    }
    readCollection {
        value.exclude(readNonNull<Spec<ReadOnlyFileTreeElement>>())
    }
}
