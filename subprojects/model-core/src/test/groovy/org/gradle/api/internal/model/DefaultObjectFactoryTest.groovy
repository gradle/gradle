/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.model

import org.gradle.api.Named
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultObjectFactoryTest extends ConcurrentSpec {
    def factory = DefaultObjectFactory.INSTANCE

    def "creates instance of Named"() {
        expect:
        def n1 = factory.named(Named, "a")
        def n2 = factory.named(Named, "b")

        n1.is(n1)
        !n1.is(n2)

        n1.name == "a"
        n2.name == "b"
    }

    def "creates instance with constructor parameters"() {
        CharSequence param = Mock()

        expect:
        def result = factory.newInstance(SomeType, param)
        result instanceof SomeType
        result.result == param
    }
}

class DummyGroovyNamed implements Named {
    String getName() { null }

    String getCalculatedValue() { "[$name]" }
}

class SomeType {
    final Object result

    SomeType(CharSequence result) {
        this.result = result
    }
}
