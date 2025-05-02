/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.options

import org.gradle.internal.reflect.JavaMethod
import spock.lang.Specification

class InstanceOptionDescriptorSpec extends Specification{

    OptionElement optionElement = optionElement("someOption")

    def testGetAvailableValuesWithNoDefaults() {
        given:
        optionElement.getAvailableValues() >> []

        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new SomeClass(), optionElement)

        then:
        descriptor.getAvailableValues() == [] as Set
    }

    def getAvailableValuesCallsWhenOptionValueMethodAvailable() {
        given:
        JavaMethod<Object, Collection> optionValueMethod = Mock(JavaMethod)

        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new SomeClass(), optionElement, optionValueMethod)
        def values = descriptor.getAvailableValues()

        then:
        values == ["dynValue1", "dynValue2"] as Set
        1 * optionValueMethod.invoke(_,_) >> ["dynValue1", "dynValue2"]
    }

    def "should sort alphabetically by name by default"() {
        given:
        InstanceOptionDescriptor optionC = new InstanceOptionDescriptor(new SomeClass(), optionElement("optionC"))
        InstanceOptionDescriptor optionA = new InstanceOptionDescriptor(new SomeClass(), optionElement("optionA"))
        InstanceOptionDescriptor optionB = new InstanceOptionDescriptor(new SomeClass(), optionElement("optionB"))
        def descriptors = Arrays.asList(optionC, optionA, optionB)

        when:
        Collections.sort(descriptors)

        then:
        descriptors[0].name == "optionA"
        descriptors[1].name == "optionB"
        descriptors[2].name == "optionC"
    }

    def optionElement(String name) {
        optionElement(name, 0)
    }

    def optionElement(String name, int order) {
        OptionElement optionElement = Mock(OptionElement)
        optionElement.getOptionName() >> name
        optionElement.getOptionType() >> String.class
        optionElement.getAvailableValues() >> []
        optionElement.getOrder() >> order
        optionElement
    }

    public class SomeClass {
    }
}


