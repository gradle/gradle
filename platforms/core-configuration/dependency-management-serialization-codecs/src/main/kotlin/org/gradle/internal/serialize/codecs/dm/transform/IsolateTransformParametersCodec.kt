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

package org.gradle.internal.serialize.codecs.dm.transform

import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.transform.DefaultTransform
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationRunner


class IsolateTransformParametersCodec(
    val parameterScheme: TransformParameterScheme,
    val isolatableFactory: IsolatableFactory,
    val buildOperationRunner: BuildOperationRunner,
    val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    val fileCollectionFactory: FileCollectionFactory,
    val documentationRegistry: DocumentationRegistry
) : Codec<DefaultTransform.IsolateTransformParameters> {
    override suspend fun WriteContext.encode(value: DefaultTransform.IsolateTransformParameters) {
        write(value.parameterObject)
        writeClass(value.implementationClass)
        writeBoolean(value.isCacheable)
    }

    override suspend fun ReadContext.decode(): DefaultTransform.IsolateTransformParameters? {
        val parameterObject: TransformParameters? = read()?.uncheckedCast()
        val implementationClass = readClass()
        val cacheable = readBoolean()

        return DefaultTransform.IsolateTransformParameters(
            parameterObject,
            implementationClass,
            cacheable,
            StandaloneDomainObjectContext.ANONYMOUS,
            parameterScheme.inspectionScheme.propertyWalker,
            isolatableFactory,
            buildOperationRunner,
            classLoaderHierarchyHasher,
            fileCollectionFactory
        )
    }
}
