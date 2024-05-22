/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull


object ResolveArtifactNodeCodec : Codec<DefaultResolvableArtifact.ResolveAction> {
    override suspend fun WriteContext.encode(value: DefaultResolvableArtifact.ResolveAction) {
        // TODO - should just discard this action. The artifact location will already have been resolved during writing to the cache
        // and so this node does not do anything useful
        write(value.artifact)
    }

    override suspend fun ReadContext.decode(): DefaultResolvableArtifact.ResolveAction {
        val artifact = readNonNull<DefaultResolvableArtifact>()
        return DefaultResolvableArtifact.ResolveAction(artifact)
    }
}
