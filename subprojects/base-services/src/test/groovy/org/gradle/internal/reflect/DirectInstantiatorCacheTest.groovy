/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.reflect

import spock.lang.Shared
import spock.lang.Specification

class DirectInstantiatorCacheTest extends Specification {

    @Shared
    def cache = new DirectInstantiator.ConstructorCache()

    def "constructor cache returns the same constructors as 'getConstructors'"() {
        expect:
        cache.get(clazz).toList() == clazz.getConstructors().toList()
        cache.cache.size() == expectedCacheSize

        where:
        clazz              | expectedCacheSize
        String             | 1
        LinkedList         | 2
        ArrayList          | 3
        JavaMethod         | 4
        String             | 4
        ArrayList          | 4
        JavaReflectionUtil | 5
    }
}
