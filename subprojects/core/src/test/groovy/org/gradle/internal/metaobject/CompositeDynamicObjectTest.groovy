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

package org.gradle.internal.metaobject

import spock.lang.Specification

class CompositeDynamicObjectTest extends Specification {
    def obj = new CompositeDynamicObject() {
        @Override
        String getDisplayName() {
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

    def "get property fails when property cannot be found"() {
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

    def "set property returns on first delegate that has the property"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        obj.setProperty("p", "value")

        then:
        1 * obj1.setProperty("p", "value", _)
        1 * obj2.setProperty("p", "value", _) >> { String name, def value, SetPropertyResult r -> r.found() }
        0 * _
    }

    def "set property fails when property cannot be found"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        obj.setProperty("p", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not set unknown property 'p' for <obj>."
    }

    def "invokes method on first delegate that has method"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        def result = obj.invokeMethod("m", ["value"] as Object[])

        then:
        result == "result"

        and:
        1 * obj1.invokeMethod("m", _, ["value"] as Object[])
        1 * obj2.invokeMethod("m", _, ["value"] as Object[]) >> { String name, InvokeMethodResult r, def args -> r.result("result") }
        0 * _
    }

    def "method may have null return value"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        def result = obj.invokeMethod("m", ["value"] as Object[])

        then:
        result == null

        and:
        1 * obj1.invokeMethod("m", _, _)
        1 * obj2.invokeMethod("m", _, _) >> { String name, InvokeMethodResult r, def args -> r.result(null) }
        0 * _
    }

    def "invoke method fails when method cannot be found"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        obj.invokeMethod("m", "value")

        then:
        def e = thrown(groovy.lang.MissingMethodException)
        e.message.startsWith("No signature of method: ${CompositeDynamicObjectTest.name}.m() is applicable for argument types: (java.lang.String) values: [value]")
    }
}
