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
import spock.lang.Unroll

class DirectInstantiatorCacheTest extends Specification {

    @Shared
    def cache = new DirectInstantiator.ConstructorCache()

    @Unroll("constructor cache returns the same constructors as 'getConstructors' for #clazz")
    def "constructor cache returns the same constructors as 'getConstructors'"() {
        given:
        def constructor = null
        int i = 0
        while (!constructor && ++i<50) {
            // need a loop because IBM JDK is much more proactive in cleaning weak references
            constructor = cache.get(clazz, [] as Class[]).method
        }
        def constructors = clazz.getConstructors().toList()

        expect:
        constructor == constructors.find { it.parameterTypes.length == 0 }
        cache.size() == expectedCacheSize

        where:
        clazz              | expectedCacheSize
        Foo                | 1
        LinkedList         | 2
        ArrayList          | 3
        HashSet            | 4
        Foo                | 4
        ArrayList          | 4
        JavaReflectionUtil | 5
    }

    static class Foo {
        public Foo() {}
    }
}
