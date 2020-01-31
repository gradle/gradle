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
import org.gradle.api.internal.tasks.TaskValidationContext
import org.gradle.api.internal.tasks.properties.BeanPropertyContext
import org.gradle.api.internal.tasks.properties.DefaultValidatingProperty
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.internal.tasks.properties.ValidationActions
import org.gradle.api.tasks.Optional
import org.gradle.internal.reflect.PropertyMetadata
import org.gradle.internal.reflect.TypeValidationContext
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class NestedBeanAnnotationHandlerTest extends Specification {

    def value = Mock(PropertyValue)
    def propertyVisitor = Mock(PropertyVisitor)
    def task = Stub(TaskInternal)
    def context = Mock(BeanPropertyContext)
    def propertyMetadata = Mock(PropertyMetadata)

    def "absent nested property is reported as error"() {
        PropertyValue validatingValue = null
        def validationContext = Mock(TaskValidationContext)

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", value, propertyMetadata, propertyVisitor, context)

        then:
        1 * value.call() >> null
        1 * propertyMetadata.isAnnotationPresent(Optional) >> false
        1 * propertyVisitor.visitInputProperty("name", _, false) >> { arguments ->
            validatingValue = arguments[1]
        }
        1 * propertyVisitor.visitOutputFilePropertiesOnly() >> false
        0 * _
        validatingValue.call() == null

        when:
        def validatingSpec = new DefaultValidatingProperty("name", validatingValue, false, ValidationActions.NO_OP)
        validatingSpec.validate(validationContext)

        then:
        1 * validationContext.visitPropertyProblem(TypeValidationContext.Severity.ERROR, "No value has been specified for property 'name'")
        0 * _
    }

    def "absent optional nested property is ignored"() {
        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", value, propertyMetadata, propertyVisitor, context)

        then:
        1 * value.call() >> null
        1 * propertyMetadata.isAnnotationPresent(Optional) >> true
        1 * propertyVisitor.visitOutputFilePropertiesOnly() >> false
        0 * _
    }

    def "exception thrown by nested property is propagated"() {
        PropertyValue validatingValue = null
        def exception = new RuntimeException("BOOM!")

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue("name", value, propertyMetadata, propertyVisitor, context)

        then:
        1 * value.call() >> {
            throw exception
        }
        1 * propertyVisitor.visitInputProperty("name", _, false) >> { arguments ->
            validatingValue = arguments[1]
        }
        1 * propertyVisitor.visitOutputFilePropertiesOnly() >> false
        0 * _

        when:
        validatingValue.call()

        then:
        0 * _
        def thrown = thrown(RuntimeException)
        exception == thrown
    }

    def "nested bean is added"() {
        def nestedBean = new Object()
        def nestedPropertyName = "someProperty"

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(nestedPropertyName, value, propertyMetadata, propertyVisitor, context)

        then:
        1 * value.call() >> nestedBean
        1 * context.addNested(nestedPropertyName, nestedBean)
        1 * propertyVisitor.visitOutputFilePropertiesOnly() >> outputPropertyFilesOnly
        0 * _

        where:
        outputPropertyFilesOnly << [true, false]
    }
}
