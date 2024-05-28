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

package org.gradle.configurationcache.serialization.codecs.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.AbstractTransformedArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformedExternalArtifactSet
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.model.CalculatedValueContainer


class TransformedExternalArtifactSetCodec : Codec<TransformedExternalArtifactSet> {
    override suspend fun WriteContext.encode(value: TransformedExternalArtifactSet) {
        encodePreservingSharedIdentityOf(value) {
            write(value.result)
        }
    }

    override suspend fun ReadContext.decode(): TransformedExternalArtifactSet {
        return decodePreservingSharedIdentity {
            val result = readNonNull<CalculatedValueContainer<ImmutableList<ResolvedArtifactSet.Artifacts>, AbstractTransformedArtifactSet.CalculateArtifacts>>()
            TransformedExternalArtifactSet(result)
        }
    }
}
