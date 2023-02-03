/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.typeconversion

import org.gradle.api.specs.Spec
import spock.lang.Specification

class ClosureToSpecNotationConverterTest extends Specification {
    private ClosureToSpecNotationConverter converter = new ClosureToSpecNotationConverter(String)

    def "converts closures"() {
        given:
        def spec = parse({ it == 'foo' })

        expect:
        spec.isSatisfiedBy("foo")
        !spec.isSatisfiedBy("FOO")
        !spec.isSatisfiedBy("bar")
    }

    def parse(def value) {
        return NotationParserBuilder.toType(Spec).fromType(Closure, converter).toComposite().parseNotation(value)
    }
}
