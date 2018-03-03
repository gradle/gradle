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

import org.gradle.api.Action
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultPropertySpecFactory
import org.gradle.api.internal.tasks.TaskInputPropertySpec
import org.gradle.api.internal.tasks.TaskValidationContext
import org.gradle.api.internal.tasks.ValidatingTaskPropertySpec
import org.gradle.api.internal.tasks.properties.NestedPropertyContext
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.util.ConfigureUtil
import spock.lang.Specification
import spock.lang.Unroll

class NestedBeanAnnotationHandlerTest extends Specification {

    def propertyValue = Mock(PropertyValue)
    def propertyVisitor = Mock(PropertyVisitor)
    def task = Stub(TaskInternal)
    def resolver = Mock(FileResolver)
    def specFactory = new DefaultPropertySpecFactory(task, resolver)
    def context = Mock(NestedPropertyContext)

    @Unroll
    def "correct implementation for #type coerced to Action is tracked"() {
        expect:
        NestedBeanAnnotationHandler.getImplementationClass(implementation as Action) == implementation.getClass()

        where:
        type      | implementation
        "Closure" | { it }
        "Action"  |  new Action<String>() { @Override void execute(String s) {} }
    }

    def "correct implementation for closure wrapped in Action is tracked"() {
        given:
        def closure = { it }

        expect:
        NestedBeanAnnotationHandler.getImplementationClass(ConfigureUtil.configureUsing(closure)) == closure.getClass()

        and:
        NestedBeanAnnotationHandler.getImplementationClass(ClosureBackedAction.of(closure)) == closure.getClass()
    }

    def "absent nested property is reported as error"() {
        ValidatingTaskPropertySpec taskInputPropertySpec = null
        def validationContext = Mock(TaskValidationContext)

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.value >> null
        1 * propertyValue.propertyName >> "name"
        1 * propertyValue.optional >> false
        1 * propertyVisitor.visitInputProperty(_) >> { arguments ->
            taskInputPropertySpec = arguments[0]
        }
        0 * _
        taskInputPropertySpec.value == null

        when:
        taskInputPropertySpec.validate(validationContext)

        then:
        1 * validationContext.recordValidationMessage(TaskValidationContext.Severity.ERROR, "No value has been specified for property 'name'.")
        0 * _
    }

    def "absent optional nested property is ignored"() {
        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.value >> null
        1 * propertyValue.optional >> true
        0 * _
    }

    def "exception thrown by nested property is propagated"() {
        ValidatingTaskPropertySpec taskInputPropertySpec = null
        def validationContext = Mock(TaskValidationContext)
        def exception = new RuntimeException("BOOM!")

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.value >> {
            throw exception
        }
        1 * propertyValue.getPropertyName() >> "name"
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
        TaskInputPropertySpec taskInputPropertySpec = null
        def nestedBean = new Object()
        def nestedPropertyName = "someProperty"

        when:
        new NestedBeanAnnotationHandler().visitPropertyValue(propertyValue, propertyVisitor, specFactory, context)

        then:
        1 * propertyValue.value >> nestedBean
        1 * propertyValue.getPropertyName() >> nestedPropertyName
        1 * context.shouldUnpack({ it.propertyName == nestedPropertyName }) >> false
        1 * context.addNested({ it.propertyName == nestedPropertyName })
        1 * propertyVisitor.visitInputProperty({ it.propertyName == "${nestedPropertyName}.class"}) >> { arguments ->
            taskInputPropertySpec = arguments[0]
        }
        0 * _

        taskInputPropertySpec.value == nestedBean.getClass()
    }
}
