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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification
import spock.lang.Unroll

class AbstractTaskPropertyBuilderTest extends Specification {

    def resolver = Mock(FileResolver)
    def spec = Spy(AbstractTaskPropertyBuilder)

    def "can register nested property name"() {
        when:
        spec.propertyName = "parent.child"
        then:
        spec.propertyName == "parent.child"
    }

    @Unroll
    def "cannot register property with invalid name '#invalidName'"() {
        when:
        spec.propertyName = invalidName
        then:
        def ex = thrown IllegalArgumentException
        ex.message == message

        where:
        invalidName | message
        ""          | "Property name must not be empty string"
        " property" | "Property name ' property' must be a valid Java identifier"
        "pro-perty" | "Property name 'pro-perty' must be a valid Java identifier"
        ".property" | "Property name '.property' must be a valid Java identifier"
        "property." | "Property name 'property.' must be a valid Java identifier"
        "pro..erty" | "Property name 'pro..erty' must be a valid Java identifier"
    }
}
