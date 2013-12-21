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

    OptionElement delegate = Mock(OptionElement)

    def setup(){
        _ * delegate.getOptionType() >> String.class
        _ * delegate.getAvailableValues() >> new ArrayList<String>()
        _ * delegate.getOptionName() >> "someOption"
    }

    def testGetAvailableValuesWithNoDefaults() {
        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new SomeClass(), delegate)
        then:
        descriptor.getAvailableValues() == []
    }

    def getAvailableValuesCallsWhenOptionValueMethodAvailable() {
        setup:
        JavaMethod<Object, Collection> optionValueMethod = Mock(JavaMethod)
        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new SomeClass(), delegate, optionValueMethod)
        List<String> values = descriptor.getAvailableValues()
        then:
        values == ["dynValue1", "dynValue2"]
        1 * optionValueMethod.invoke(_,_) >> ["dynValue1", "dynValue2"]
    }

    public class SomeClass {
    }
}


