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

package org.gradle.internal.serialize.codecs.dm.transform

import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.transform.Transform
import org.gradle.api.internal.artifacts.transform.TransformInvocationFactory
import org.gradle.api.internal.artifacts.transform.TransformStep
import org.gradle.internal.cc.base.serialize.readProjectRef
import org.gradle.internal.cc.base.serialize.writeProjectRef
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull


class TransformStepCodec(
    private val inputFingerprinter: InputFingerprinter
) : Codec<TransformStep> {

    override suspend fun WriteContext.encode(value: TransformStep) {
        encodePreservingSharedIdentityOf(value) {
            val project = value.owningProject ?: throw UnsupportedOperationException("TransformStep must have an owning project to be encoded.")
            writeProjectRef(project)
            write(value.transform)
        }
    }

    override suspend fun ReadContext.decode(): TransformStep {
        return decodePreservingSharedIdentity {
            val project = readProjectRef()
            val transform = readNonNull<Transform>()
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
