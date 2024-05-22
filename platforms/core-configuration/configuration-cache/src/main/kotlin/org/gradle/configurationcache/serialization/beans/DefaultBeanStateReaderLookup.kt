/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.serialization.beans

import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.BeanStateReaderLookup
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap


@ServiceScope(Scope.BuildTree::class)
internal
class DefaultBeanStateReaderLookup(
    private val constructors: BeanConstructors,
    private val instantiatorFactory: InstantiatorFactory
) : BeanStateReaderLookup {
    private
    val beanStateReaders = ConcurrentHashMap<Class<*>, BeanStateReader>()

    override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
        beanStateReaders.computeIfAbsent(beanType) { type -> BeanPropertyReader(type, constructors, instantiatorFactory) }
}
