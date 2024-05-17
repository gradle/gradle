/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.serialize

import org.gradle.internal.reflect.JavaReflectionUtil

class DefaultSerializerTest extends SerializerSpec {
    def canSerializeAndDeserializeObject() {
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().classLoader)
        DefaultSerializer serializer = new DefaultSerializer(classLoader)

        Class cl = classLoader.parseClass('package org.gradle.cache; class TestObj implements Serializable { }')
        Object o = JavaReflectionUtil.newInstance(cl)

        when:
        def r = serialize(o, serializer)

        then:
        cl.isInstance(r)
    }

    def "returns true when testing equality between two serializer with the same classloader"() {
        DefaultSerializer rhsSerializer = new DefaultSerializer(getClass().classLoader)
        DefaultSerializer lhsSerializer = new DefaultSerializer(getClass().classLoader)

        when:
        def areEquals = rhsSerializer.equals(lhsSerializer);

        then:
        areEquals
    }
}
