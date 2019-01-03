/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.annotations

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultPropertySpecFactory
import org.gradle.api.internal.tasks.TaskValidationContext
import org.gradle.api.internal.tasks.ValidatingTaskPropertySpec
import org.gradle.api.internal.tasks.properties.BeanPropertyContext
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import spock.lang.Specification

class NestedBeanAnnotationHandlerTest extends Specification {

    def propertyValue = Mock(PropertyValue)
    def propertyVisitor = Mock(PropertyVisitor)
    def task = Stub(TaskInternal)
    def resolver = Mock(FileResolver)
    def specFactory = new DefaultPropertySpecFactory(task, resolver)
    def context = Mock(BeanPropertyContext)

    def "absent nested property is reported as error"() {
        ValidatingTaskPropertySpec taskInputPropertySpec = null
        def validationContext = Mock(TaskValidationContext)

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.call() >> null
        1 * propertyValue.optional >> false
        1 * propertyVisitor.visitInputProperty(_) >> { arguments ->
            taskInputPropertySpec = arguments[0]
        }
        0 * _
        taskInputPropertySpec.value == null

        when:
        taskInputPropertySpec.validate(validationContext)

        then:
        1 * validationContext.recordValidationMessage("No value has been specified for property 'name'.")
        0 * _
    }

    def "absent optional nested property is ignored"() {
        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.call() >> null
        1 * propertyValue.optional >> true
        0 * _
    }

    def "exception thrown by nested property is propagated"() {
        ValidatingTaskPropertySpec taskInputPropertySpec = null
        def validationContext = Mock(TaskValidationContext)
        def exception = new RuntimeException("BOOM!")

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.call() >> {
            throw exception
        }
        1 * propertyVisitor.visitInputProperty(_) >> { arguments ->
            taskInputPropertySpec = arguments[0]
        }
        0 * _

        when:
        taskInputPropertySpec.validate(validationContext)

        then:
        0 * _
        def thrown = thrown(RuntimeException)
        exception == thrown
    }

    def "nested bean is added"() {
        def nestedBean = new Object()
        def nestedPropertyName = "someProperty"

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(nestedPropertyName, propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.call() >> nestedBean
        1 * context.addNested(nestedPropertyName, nestedBean)
        0 * _
    }
}
