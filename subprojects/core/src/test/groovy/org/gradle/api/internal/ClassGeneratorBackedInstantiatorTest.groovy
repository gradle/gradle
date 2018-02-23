/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import spock.lang.Specification
import org.gradle.internal.reflect.Instantiator

class ClassGeneratorBackedInstantiatorTest extends Specification {
    final ClassGenerator classGenerator = Mock()
    final Instantiator target = Mock()
    final ClassGeneratorBackedInstantiator instantiator = new ClassGeneratorBackedInstantiator(classGenerator, target)

    def "decorates class and instantiates instance"() {
        when:
        def result = instantiator.newInstance(CharSequence.class, "test")

        then:
        result == "[test]"
        1 * classGenerator.generate(CharSequence.class) >> String.class
        1 * target.newInstance(String.class, "test") >> "[test]"
        0 * _._
    }
}
