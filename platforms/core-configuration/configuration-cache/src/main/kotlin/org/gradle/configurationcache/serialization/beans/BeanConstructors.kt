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

package org.gradle.configurationcache.serialization.beans

import groovy.lang.GroovyObjectSupport
import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import sun.reflect.ReflectionFactory
import java.lang.reflect.Constructor


/**
 * A global service that caches the serialization constructors for bean types.
 */
@ServiceScope(Scope.Global::class)
class BeanConstructors(
    cacheFactory: CrossBuildInMemoryCacheFactory
) {
    private
    val cache: CrossBuildInMemoryCache<Class<*>, Constructor<out Any>> = cacheFactory.newClassCache()

    fun constructorForSerialization(beanType: Class<*>): Constructor<out Any> {
        return cache.get(beanType) { -> createConstructor(beanType) }
    }

    private
    fun createConstructor(beanType: Class<*>): Constructor<out Any> {
        // Initialize the super types of the bean type, as this does not seem to happen via the generated constructors
        maybeInit(beanType)
        if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
            // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
            return newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
        } else {
            return newConstructorForSerialization(beanType, Object::class.java.getConstructor())
        }
    }

    private
    fun maybeInit(beanType: Class<*>) {
        val superclass = beanType.superclass
        if (superclass?.classLoader != null) {
            Class.forName(superclass.name, true, superclass.classLoader)
            maybeInit(superclass)
        }
    }

    // TODO: What about the runtime decorations a serialized bean might have had at configuration time?
    private
    fun newConstructorForSerialization(beanType: Class<*>, constructor: Constructor<*>): Constructor<out Any> =
        ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, constructor)
}
