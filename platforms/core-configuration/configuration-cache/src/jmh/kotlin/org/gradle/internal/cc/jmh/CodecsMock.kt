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

package org.gradle.internal.cc.jmh

import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.serialize.ConfigurationCacheCodecs
import org.gradle.internal.cc.impl.serialize.DefaultConfigurationCacheCodecs
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.withIsolate
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock


internal
inline fun <R> MutableIsolateContext.withIsolateMock(codec: Codec<Any?>, block: () -> R): R =
    withIsolate(IsolateOwners.OwnerGradle(mock()), codec) {
        block()
    }


internal
fun userTypesCodec() = codecs().userTypesCodec()


private
fun codecs(): ConfigurationCacheCodecs = DefaultConfigurationCacheCodecs(
    modelParameters = modelParameters(),
    directoryFileTreeFactory = mock(),
    fileCollectionFactory = mock(),
    artifactSetConverter = mock(),
    fileLookup = mock(),
    propertyFactory = mock(),
    filePropertyFactory = mock(),
    fileResolver = mock(),
    instantiator = mock(),
    instantiatorFactory = mock(),
    fileSystemOperations = mock(),
    inputFingerprinter = mock(),
    buildOperationRunner = mock(),
    classLoaderHierarchyHasher = mock(),
    isolatableFactory = mock(),
    managedFactoryRegistry = mock(),
    parameterScheme = mock(),
    actionScheme = mock(),
    attributesFactory = mock(),
    attributeDesugaring = mock(),
    attributeSchemaFactory = mock(),
    calculatedValueContainerFactory = mock(),
    patternSetFactory = mock(),
    fileOperations = mock(),
    fileFactory = mock(),
    includedTaskGraph = mock(),
    buildStateRegistry = mock(),
    documentationRegistry = mock(),
    taskDependencyFactory = mock(),
    javaSerializationEncodingLookup = JavaSerializationEncodingLookup(),
    transformStepNodeFactory = mock(),
    problems = mock(),
)

private
fun modelParameters(): BuildModelParameters = mock {
    on { isConfigurationCacheParallelStore } doReturn false
    on { isConfigurationCacheParallelLoad } doReturn false
}
