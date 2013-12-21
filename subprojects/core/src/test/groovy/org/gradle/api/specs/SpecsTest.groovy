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

package org.gradle.api.specs

import spock.lang.Issue
import spock.lang.Specification

class SpecsTest extends Specification {

    def "can convert closures to specs"() {
        given:
        def spec = Specs.convertClosureToSpec {
            it > 10
        }

        expect:
        !spec.isSatisfiedBy(5)
        spec.isSatisfiedBy(15)
    }

    @Issue("GRADLE-2288")
    def "closure specs use groovy truth"() {
        def spec = Specs.convertClosureToSpec {
            it
        }

        expect:
        !spec.isSatisfiedBy("")
        spec.isSatisfiedBy([1,2,3])
    }
}
