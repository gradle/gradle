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

import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.transform.Transform
import org.gradle.api.internal.artifacts.transform.TransformInvocationFactory
import org.gradle.api.internal.artifacts.transform.TransformStep
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.configurationcache.serialization.codecs.getProject
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.execution.InputFingerprinter


internal
class TransformStepCodec(
    private val inputFingerprinter: InputFingerprinter
) : Codec<TransformStep> {

    override suspend fun WriteContext.encode(value: TransformStep) {
        encodePreservingSharedIdentityOf(value) {
            val project = value.owningProject ?: throw UnsupportedOperationException("TransformStep must have an owning project to be encoded.")
            writeString(project.path)
            write(value.transform)
        }
    }

    override suspend fun ReadContext.decode(): TransformStep {
        return decodePreservingSharedIdentity {
            val path = readString()
            val transform = readNonNull<Transform>()
            val project = getProject(path)
            val services = project.services
            TransformStep(
                transform,
                services[TransformInvocationFactory::class.java],
                services[DomainObjectContext::class.java],
                inputFingerprinter
            )
        }
    }
}
