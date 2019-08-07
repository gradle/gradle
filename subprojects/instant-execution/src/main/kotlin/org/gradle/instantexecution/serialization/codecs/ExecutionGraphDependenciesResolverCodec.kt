/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.ExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.Transformer
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.Try
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprinter


/**
 * TODO - recreate the dependencies resolver.
 */
object ExecutionGraphDependenciesResolverCodec : Codec<ExecutionGraphDependenciesResolver> {
    override suspend fun WriteContext.encode(value: ExecutionGraphDependenciesResolver) = Unit

    override suspend fun ReadContext.decode(): ExecutionGraphDependenciesResolver = EmptyExecutionGraphDependenciesResolver
}


private
object EmptyExecutionGraphDependenciesResolver : ExecutionGraphDependenciesResolver {
    override fun forTransformer(transformer: Transformer): Try<ArtifactTransformDependencies> {
        return Try.successful(NoDependencies)
    }

    override fun computeDependencyNodes(transformationStep: TransformationStep) = TaskDependencyContainer.EMPTY
}


private
object NoDependencies : ArtifactTransformDependencies {
    override fun getFiles(): FileCollection {
        return ImmutableFileCollection.of()
    }

    override fun fingerprint(fingerprinter: FileCollectionFingerprinter): CurrentFileCollectionFingerprint {
        return fingerprinter.empty()
    }
}
