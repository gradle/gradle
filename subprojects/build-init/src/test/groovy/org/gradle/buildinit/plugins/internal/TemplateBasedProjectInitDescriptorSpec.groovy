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

package org.gradle.buildinit.plugins.internal

import spock.lang.Specification

class TemplateBasedProjectInitDescriptorSpec extends Specification {

    def "executes all passed in template operations when generating project"() {
        setup:
        TemplateOperation templateOperation1 = Mock(TemplateOperation)
        TemplateOperation templateOperation2 = Mock(TemplateOperation)
        TemplateBasedProjectInitDescriptor descriptor = new TestTemplateBasedProjectInitDescriptor(templateOperation1, templateOperation2)
        when:
        descriptor.generate()
        then:

        then:
        1 * templateOperation1.generate()
        1 * templateOperation2.generate()
    }

    class TestTemplateBasedProjectInitDescriptor extends TemplateBasedProjectInitDescriptor{
        TestTemplateBasedProjectInitDescriptor(TemplateOperation... templateOperations) {
            templateOperations.each {
                register(it)
            }
        }
    }
}
