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

package org.gradle.api.internal

import spock.lang.Specification

class CompositeDynamicObjectTest extends Specification {
    def obj = new CompositeDynamicObject() {
        @Override
        protected String getDisplayName() {
            return "<obj>"
        }
    }

    def "get property returns result from first delegate that has the property"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        def result = obj.getProperty("p")

        then:
        result == 12

        and:
        1 * obj1.getProperty("p", _)
        1 * obj2.getProperty("p", _) >> { String name, GetPropertyResult r -> r.result(12) }
        0 * _
    }

    def "property can have null value"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        def result = obj.getProperty("p")

        then:
        result == null

        and:
        1 * obj1.getProperty("p", _)
        1 * obj2.getProperty("p", _) >> { String name, GetPropertyResult r -> r.result(null) }
        0 * _
    }

    def "fails when property cannot be found"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        obj.getProperty("p")

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not get unknown property 'p' for <obj>."
    }

}
