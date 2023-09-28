/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.util.TestUtil
import spock.lang.Specification

class DslObjectTest extends Specification {
    
    def "fails lazily for non dsl object"() {
        when:
        def dsl = new DslObject(new Object())

        then:
        notThrown(Exception)

        when:
        dsl.asDynamicObject

        then:
        thrown(IllegalStateException)
    }

    static class Thing {}

    def "works for dsl object"() {
        when:
        new DslObject(TestUtil.instantiatorFactory().decorateLenient().newInstance(Thing))

        then:
        notThrown(Exception)
    }
}
