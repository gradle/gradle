/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultGraphStructure
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.writeCollection

class ResolvedGraphResultCodec(
    private val graphStructureCodec: DefaultGraphStructureCodec
) : Codec<ResolvedGraphResult> {

    override suspend fun WriteContext.encode(value: ResolvedGraphResult) {
        encodePreservingSharedIdentityOf(value) {
            graphStructureCodec.run {
                encode(value.structure() as DefaultGraphStructure)
            }
            val availableVariantsByComponent = value.availableVariantsByComponent()
            if (availableVariantsByComponent != null) {
                writeBoolean(true)
                writeCollection(availableVariantsByComponent) {
                    writeCollection(it) { variant ->
                        write(variant)
                    }
                }
            } else {
                writeBoolean(false)
            }
        }
    }

    override suspend fun ReadContext.decode(): ResolvedGraphResult {
        return decodePreservingSharedIdentity {
            val structure = graphStructureCodec.run {
                decode()
            }
            val availableVariantsByComponent = readBoolean().let {
                if (it) {
                    readList {
                        readList {
                            read() as ResolvedVariantResult
                        }
                    }
                } else {
                    null
                }
            }
            ResolvedGraphResult(structure, availableVariantsByComponent)
        }
    }

}
