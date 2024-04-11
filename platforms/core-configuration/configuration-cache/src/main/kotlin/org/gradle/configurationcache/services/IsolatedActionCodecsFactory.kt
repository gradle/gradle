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

package org.gradle.configurationcache.services

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory.ValueSourceProvider
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.configurationcache.serialization.codecs.BeanCodec
import org.gradle.configurationcache.serialization.codecs.Bindings
import org.gradle.configurationcache.serialization.codecs.BindingsBuilder
import org.gradle.configurationcache.serialization.codecs.FixedValueReplacingProviderCodec
import org.gradle.configurationcache.serialization.codecs.PropertyCodec
import org.gradle.configurationcache.serialization.codecs.ProviderCodec
import org.gradle.configurationcache.serialization.codecs.ServicesCodec
import org.gradle.configurationcache.serialization.codecs.baseTypes
import org.gradle.configurationcache.serialization.codecs.groovyCodecs
import org.gradle.configurationcache.serialization.codecs.jos.ExternalizableCodec
import org.gradle.configurationcache.serialization.codecs.jos.JavaObjectSerializationCodec
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.serialization.codecs.unsupportedTypes
import org.gradle.configurationcache.serialization.unsupported
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildTree::class)
internal
class IsolatedActionCodecsFactory(

    private
    val javaSerializationEncodingLookup: JavaSerializationEncodingLookup,

    private
    val propertyFactory: PropertyFactory

) {
    fun isolatedActionCodecs() = Bindings.of {
        allUnsupportedTypes()
        baseTypes()
        supportedPropertyTypes()
        groovyCodecs()
        bind(ExternalizableCodec)
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
     * be captured as part of the serialized action [environment][org.gradle.configurationcache.isolation.SerializedAction.environment]
     **/
    private
    fun BindingsBuilder.unsupportedProviderTypes() {
        bind(unsupported<ValueSourceProvider<*, *>>())
        bind(unsupported<BuildServiceProvider<*, *>>())
    }
}
