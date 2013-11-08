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

import spock.lang.Specification

class InstanceOptionDescriptorSpec extends Specification{

    OptionDescriptor delegate = Mock(OptionDescriptor)

    def setup(){
        _ * delegate.getArgumentType() >> String.class
        _ * delegate.getAvailableValues() >> new ArrayList<String>()
        _ * delegate.getName() >> "someOption"
    }

    def testGetAvailableValuesWithNoDefaults() {
        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new NoOptionValues(), delegate)
        then:
        descriptor.getAvailableValues() == []
    }

    def testGetAvailableValuesWithDefaults() {
        when:
        InstanceOptionDescriptor descriptor = new InstanceOptionDescriptor(new SomeOptionValues(), delegate)
        then:
        descriptor.getAvailableValues() == ["something"]
    }

    def testGetAvailableValuesInvalidAnnotation() {
        when:
        new InstanceOptionDescriptor(new WithInvalidSomeOptionMethod(), delegate).getAvailableValues()
        then:
        def e = thrown(OptionValidationException)
        e.message == "OptionValues annotation not supported on method getValues in class org.gradle.api.internal.tasks.options.WithInvalidSomeOptionMethod. Supported method must return Collection and take no parameters";

        when:
        new InstanceOptionDescriptor(new WithDuplicateSomeOptions(), delegate).getAvailableValues()
        then:
        e = thrown(OptionValidationException)
        e.message == "OptionValues for someOption cannot be attached to multiple methods in class org.gradle.api.internal.tasks.options.WithDuplicateSomeOptions.";
    }
}

public class WithInvalidSomeOptionMethod {
    @OptionValues("someOption")
    List<String> getValues(String someParam) { return Arrays.asList("something")}
}

public class WithDuplicateSomeOptions {
    @OptionValues("someOption")
    List<String> getValues() { return Arrays.asList("something")}

    @OptionValues("someOption")
    List<String> getValues2() { return Arrays.asList("somethingElse")}
}

public class NoOptionValues{
}

public class SomeOptionValues{
    @OptionValues("someOption")
    List<String> getValues() { return Arrays.asList("something")}
}
