/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.tasks.properties.InputFilePropertyType
import spock.lang.Specification


class DefaultDeclaredTaskInputFilePropertyTest extends Specification {
    def "notifies property value of start and end of task execution when it implements lifecycle interface"() {
        def value = Mock(LifecycleAwareTaskProperty)
        def valueWrapper = Stub(ValidatingValue)
        def property = new DefaultDeclaredTaskInputFileProperty("task", Stub(FileResolver), valueWrapper, InputFilePropertyType.FILES)

        given:
        valueWrapper.call() >> value

        when:
        property.prepareValue()

        then:
        1 * value.prepareValue()
        0 * value._

        when:
        property.cleanupValue()

        then:
        1 * value.cleanupValue()
        0 * value._
    }

    def "does not notify null property value"() {
        def valueWrapper = Stub(ValidatingValue)
        def property = new DefaultDeclaredTaskInputFileProperty("task", Stub(FileResolver), valueWrapper, InputFilePropertyType.FILES)

        given:
        valueWrapper.call() >> null

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }

    def "does not notify property value that does not implement lifecycle interface"() {
        def valueWrapper = Stub(ValidatingValue)
        def property = new DefaultDeclaredTaskInputFileProperty("task", Stub(FileResolver), valueWrapper, InputFilePropertyType.FILES)

        given:
        valueWrapper.call() >> "thing"

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }
}
