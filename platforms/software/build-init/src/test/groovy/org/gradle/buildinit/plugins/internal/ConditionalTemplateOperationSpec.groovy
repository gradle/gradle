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


class ConditionalTemplateOperationSpec extends Specification {

    def "triggers delegates depending on condition"(){
        TemplateOperation operation1 = Mock(TemplateOperation)
        TemplateOperation operation2 = Mock(TemplateOperation)

        Mock(org.gradle.internal.Factory).create() >> true
        when:
        new ConditionalTemplateOperation({true} as org.gradle.internal.Factory, [operation1, operation2]).generate()
        then:
        1 * operation1.generate()
        1 * operation2.generate()

        when:
        new ConditionalTemplateOperation({false} as org.gradle.internal.Factory, [operation1, operation2]).generate()
        then:
        0 * operation1.generate()
        0 * operation2.generate()
    }
}
