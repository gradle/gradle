/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.state

import org.gradle.model.Managed
import org.gradle.model.internal.manage.schema.extraction.DefaultModelSchemaStore
import spock.lang.Specification

class ManagedModelElementTest extends Specification {

    def schemas = new DefaultModelSchemaStore(null)

    def element = new ManagedModelElement<MultipleProps>(schemas.getSchema(MultipleProps))

    @Managed
    static interface MultipleProps {
        String getProp1();
        void setProp1(String string);
        String getProp2();
        void setProp2(String string);
        String getProp3();
        void setProp3(String string);
    }

    def "can create managed element"() {
        when:
        element.get(String, "prop1").set("foo")

        then:
        element.get(String, "prop1").get() == "foo"
    }

    def "an error is raised when property type is different than requested"() {
        when:
        element.get(Object, "prop1")

        then:
        UnexpectedModelPropertyTypeException e = thrown()
        e.message == "Expected property 'prop1' for type '$MultipleProps.name' to be of type '$Object.name' but it actually is of type '$String.name'"
    }
}
