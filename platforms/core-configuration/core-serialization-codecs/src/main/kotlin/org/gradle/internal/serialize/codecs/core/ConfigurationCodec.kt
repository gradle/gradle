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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.internal.serialize.graph.codecs.WideningCodec


/**
 * Encodes and decodes [org.gradle.api.artifacts.Configuration] instances by
 * delegating entirely to [FileCollectionCodec] (a [Configuration][org.gradle.api.artifacts.Configuration]
 * is a [FileCollectionInternal]). Adds a [WideningCodec] declaration so that
 * a `Configuration` value flowing into a field or `Property<T>` whose declared
 * type cannot accept the codec's `FileCollectionInternal` decode result is
 * rejected at configuration cache store time.
 *
 * Must be registered before [FileCollectionCodec] in the bindings list — the
 * binding-walk picks the first match for a runtime type, and `Configuration`
 * is a subtype of `FileCollectionInternal`.
 */
class ConfigurationCodec(
    fileCollectionFactory: FileCollectionFactory,
    artifactSetConverter: ArtifactSetToFileCollectionFactory,
    taskDependencyFactory: TaskDependencyFactory
) : FileCollectionCodec(fileCollectionFactory, artifactSetConverter, taskDependencyFactory),
    WideningCodec<FileCollectionInternal> {

    override val decodedType: Class<FileCollectionInternal> = FileCollectionInternal::class.java

    override val publicDecodedType: Class<*> = FileCollection::class.java

    override val wideningFix: String = "Use a ConfigurableFileCollection instead."
}
