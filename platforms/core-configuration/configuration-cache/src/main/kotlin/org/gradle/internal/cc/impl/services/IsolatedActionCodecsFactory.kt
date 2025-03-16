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

package org.gradle.internal.cc.impl.services

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileFactory
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory.ValueSourceProvider
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.internal.cc.impl.serialize.baseTypes
import org.gradle.internal.serialize.codecs.core.DirectoryCodec
import org.gradle.internal.serialize.codecs.core.DirectoryPropertyCodec
import org.gradle.internal.serialize.codecs.core.FixedValueReplacingProviderCodec
import org.gradle.internal.serialize.codecs.core.ListPropertyCodec
import org.gradle.internal.serialize.codecs.core.LoggerCodec
import org.gradle.internal.serialize.codecs.core.MapPropertyCodec
import org.gradle.internal.serialize.codecs.core.PropertyCodec
import org.gradle.internal.serialize.codecs.core.ProviderCodec
import org.gradle.internal.serialize.codecs.core.RegularFileCodec
import org.gradle.internal.serialize.codecs.core.RegularFilePropertyCodec
import org.gradle.internal.serialize.codecs.core.SetPropertyCodec
import org.gradle.internal.serialize.codecs.core.groovyCodecs
import org.gradle.internal.serialize.codecs.core.jos.ExternalizableCodec
import org.gradle.internal.serialize.codecs.core.jos.JavaObjectSerializationCodec
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.serialize.codecs.core.unsupportedTypes
import org.gradle.internal.serialize.codecs.stdlib.ProxyCodec
import org.gradle.internal.serialize.graph.codecs.BeanCodec
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.codecs.ServicesCodec
import org.gradle.internal.serialize.graph.unsupported
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildTree::class)
internal
class IsolatedActionCodecsFactory(

    private
    val javaSerializationEncodingLookup: JavaSerializationEncodingLookup,

    private
    val propertyFactory: PropertyFactory,

    private
    val filePropertyFactory: FilePropertyFactory,

    private
    val fileFactory: FileFactory

) {
    fun isolatedActionCodecs() = Bindings.of {
        allUnsupportedTypes()
        baseTypes()
        supportedPropertyTypes()
        groovyCodecs()
        bind(ExternalizableCodec)

        bind(RegularFileCodec(fileFactory))
        bind(DirectoryCodec(fileFactory))

        bind(LoggerCodec)
        bind(ProxyCodec)

        bind(ServicesCodec)

        bind(JavaObjectSerializationCodec(javaSerializationEncodingLookup))
        bind(BeanCodec)
    }.build()

    private
    fun BindingsBuilder.allUnsupportedTypes() {
        unsupportedTypes()
        unsupportedProviderTypes()
        unsupported<FileCollection>()
    }

    private
    fun BindingsBuilder.supportedPropertyTypes() {
        val valueReplacingProviderCodec = fixedValueReplacingProviderCodec()
        bind(RegularFilePropertyCodec(filePropertyFactory, valueReplacingProviderCodec))
        bind(DirectoryPropertyCodec(filePropertyFactory, valueReplacingProviderCodec))
        bind(SetPropertyCodec(propertyFactory, valueReplacingProviderCodec))
        bind(MapPropertyCodec(propertyFactory, valueReplacingProviderCodec))
        bind(ListPropertyCodec(propertyFactory, valueReplacingProviderCodec))
        bind(PropertyCodec(propertyFactory, valueReplacingProviderCodec))
        bind(ProviderCodec(valueReplacingProviderCodec))
    }

    private
    fun fixedValueReplacingProviderCodec() =
        FixedValueReplacingProviderCodec(
            Bindings.of {
                unsupportedProviderTypes()
                bind(BeanCodec)
            }.build()
        )

    /**
     * Value sources and build services are currently unsupported but could eventually
     * be captured as part of the serialized action [environment][org.gradle.internal.cc.impl.isolation.SerializedIsolatedActionGraph.environment]
     **/
    private
    fun BindingsBuilder.unsupportedProviderTypes() {
        bind(unsupported<ValueSourceProvider<*, *>>())
        bind(unsupported<BuildServiceProvider<*, *>>())
    }
}
