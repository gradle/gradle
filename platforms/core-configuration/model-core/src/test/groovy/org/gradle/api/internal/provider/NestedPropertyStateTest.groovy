/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider

import org.gradle.internal.Describables
import org.gradle.internal.state.ModelObject

class NestedPropertyStateTest extends DefaultPropertyTest {
    @Override
    DefaultProperty propertyWithDefaultValue(Class type) {
        def property = super.propertyWithDefaultValue(type)
        // Exercise the full scalar-property contract with nested state, without changing the fixture's display name.
        property.setValueState(new NestedPropertyState(property.getValueState(), Stub(ModelObject), Describables.of("nested property")))
        return property
    }

    def "attachment preserves the existing value and convention through an earlier reference"() {
        given:
        def property = new DefaultProperty<String>(host, String)
        property.convention("convention")
        property.set("explicit")
        def earlierReference = property

        when:
        property.attachNestedOwner(Stub(ModelObject), Describables.of("nested property"))

        then:
        earlierReference.get() == "explicit"

        when:
        earlierReference.unset()

        then:
        property.get() == "convention"

        when:
        earlierReference.set("replacement")

        then:
        property.get() == "replacement"
    }

    def "attachment preserves #restriction configured beforehand"() {
        given:
        def property = new DefaultProperty<String>(host, String)
        property.set("value")
        property."$restriction"()

        when:
        property.attachNestedOwner(Stub(ModelObject), Describables.of("nested property"))

        then:
        property.isFinalized() == finalizedBeforeRead
        property.get() == "value"
        property.isFinalized() == finalizedAfterRead

        when:
        property.set("replacement")

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "The value for nested property ${finalizedAfterRead ? 'is final and cannot' : 'cannot'} be changed any further."

        where:
        restriction           | finalizedBeforeRead | finalizedAfterRead
        "disallowChanges"     | false               | false
        "finalizeValue"       | true                | true
        "finalizeValueOnRead" | false               | true
        "disallowUnsafeRead"  | false               | true
    }

    def "attachment preserves unsafe-read validation from the original property host"() {
        given:
        def property = new DefaultProperty<String>(host, String)
        property.set("value")
        property.disallowUnsafeRead()
        property.attachNestedOwner(Stub(ModelObject), Describables.of("nested property"))

        when:
        property.get()

        then:
        1 * host.beforeRead(null) >> "configuration is incomplete"
        def failure = thrown(IllegalStateException)
        failure.message == "Cannot query the value of nested property because configuration is incomplete."
    }
}
