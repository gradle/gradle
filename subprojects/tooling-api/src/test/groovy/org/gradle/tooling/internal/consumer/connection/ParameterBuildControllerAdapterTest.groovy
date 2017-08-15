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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.Action
import org.gradle.testing.internal.util.Specification
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalProtocolInterface
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.Element
import org.gradle.tooling.model.gradle.GradleBuild

class ParameterBuildControllerAdapterTest extends Specification {
    def internalController = Mock(InternalBuildControllerVersion2)
    def graphAdapter = Mock(ObjectGraphAdapter)
    def adapter = Mock(ProtocolToModelAdapter) {
        newGraph() >> graphAdapter
    }
    def mapping = Stub(ModelMapping) {
        getModelIdentifierFromModelType(_) >> { Class type ->
            return Stub(ModelIdentifier) {
                getName() >> type.simpleName
            }
        }
    }
    def controller = new ParameterBuildControllerAdapter(adapter, internalController, mapping, new File("root"))

    def "unpacks unsupported model exception"() {
        def failure = new RuntimeException()

        given:
        _ * internalController.getModel(null, _, null) >> { throw new InternalUnsupportedModelException().initCause(failure) }

        when:
        controller.getModel(String)

        then:
        UnknownModelException e = thrown()
        e.message == /No model of type 'String' is available in this build./
        e.cause == failure
    }

    def "fetches model for target object without parameter"() {
        def model = new Object()
        def targetElement = new Object()
        def modelElement = Stub(Element)
        def modelView = Stub(GradleBuild)

        when:
        def result = controller.getModel(modelElement, GradleBuild, null, null)

        then:
        result == modelView

        and:
        1 * adapter.unpack(modelElement) >> targetElement
        1 * internalController.getModel(targetElement, _, null) >> { def target, ModelIdentifier identifier, parameter ->
            assert identifier.name == 'GradleBuild'
            assert parameter == null
            return Stub(BuildResult) {
                getModel() >> model
            }
        }
        1 * graphAdapter.builder(GradleBuild) >> Stub(ViewBuilder) {
            build(model) >> modelView
        }
    }

    def "fetches model for target object with parameter"() {
        def model = new Object()
        def targetElement = new Object()
        def modelElement = Stub(Element)
        def modelView = Stub(GradleBuild)
        def parameterType = ValidParameter
        def parameterInitializer = new Action<ValidParameter>() {
            @Override
            void execute(ValidParameter parameter) {
                parameter.setValue("myValue")
                parameter.setBooleanValue(true)
            }
        }

        when:
        def result = controller.getModel(modelElement, GradleBuild, parameterType, parameterInitializer)

        then:
        result == modelView

        and:
        1 * adapter.unpack(modelElement) >> targetElement
        1 * internalController.getModel(targetElement, _, _) >> { def target, ModelIdentifier identifier, ValidParameter parameter ->
            assert identifier.name == 'GradleBuild'
            assert parameter.getValue() == "myValue"
            assert parameter.isBooleanValue()
            return Stub(BuildResult) {
                getModel() >> model
            }
        }
        1 * graphAdapter.builder(GradleBuild) >> Stub(ViewBuilder) {
            build(model) >> modelView
        }
    }

    def "fetches missing model for target object"() {
        def targetElement = new Object()
        def modelElement = Stub(Element)

        when:
        def result = controller.findModel(modelElement, GradleBuild, null, null)

        then:
        result == null

        and:
        1 * adapter.unpack(modelElement) >> targetElement
        1 * internalController.getModel(targetElement, _, _) >> { throw new InternalUnsupportedModelException() }
    }

    def "fetches build model"() {
        def model = Stub(InternalProtocolInterface)
        def modelView = Stub(GradleBuild)

        when:
        def result = controller.buildModel

        then:
        result == modelView

        and:
        1 * internalController.getModel(null, _, _) >> { def target, ModelIdentifier identifier, parameter ->
            assert identifier.name == 'GradleBuild'
            assert parameter == null
            return Stub(BuildResult) {
                getModel() >> model
            }
        }
        1 * graphAdapter.builder(GradleBuild) >> Stub(ViewBuilder) {
            build(model) >> modelView
        }
    }

    def "fetches missing model"() {
        when:
        def result = controller.findModel(GradleBuild)

        then:
        result == null

        and:
        1 * internalController.getModel(null, _, _) >> { throw new InternalUnsupportedModelException() }
    }

    def "throws exception when parameter not valid"() {
        when:
        controller.getModel(null, GradleBuild, InvalidParameter1, new Action<InvalidParameter1>() {
            @Override
            void execute(InvalidParameter1 invalidParameter) {}
        })

        then:
        UnknownModelException e1 = thrown()
        e1.message == "Invalid parameter."

        when:
        controller.getModel(null, GradleBuild, InvalidParameter2, new Action<InvalidParameter2>() {
            @Override
            void execute(InvalidParameter2 invalidParameter) {}
        })

        then:
        UnknownModelException e2 = thrown()
        e2.message == "Invalid parameter."

        when:
        controller.getModel(null, GradleBuild, InvalidParameter3, new Action<InvalidParameter3>() {
            @Override
            void execute(InvalidParameter3 invalidParameter) {}
        })

        then:
        UnknownModelException e3 = thrown()
        e3.message == "Invalid parameter."

        when:
        controller.getModel(null, GradleBuild, InvalidParameter4, new Action<InvalidParameter4>() {
            @Override
            void execute(InvalidParameter4 invalidParameter) {}
        })

        then:
        UnknownModelException e4 = thrown()
        e4.message == "Invalid parameter."

        when:
        controller.getModel(null, GradleBuild, InvalidParameter5, new Action<InvalidParameter5>() {
            @Override
            void execute(InvalidParameter5 invalidParameter5) {}
        })

        then:
        UnknownModelException e5 = thrown()
        e5.message == "Invalid parameter."
    }

    interface ValidParameter {
        void setValue(String value)
        String getValue()
        void setBooleanValue(boolean booleanValue)
        boolean isBooleanValue()
    }

    interface InvalidParameter1 {
        void notASetterOrGetter();
    }

    interface InvalidParameter2 {
        void setValue()
        String getValue()
    }

    interface InvalidParameter3 {
        void setValue(Integer value)
        String getValue()
    }

    interface InvalidParameter4 {
        void setValue(String value)
    }

    interface InvalidParameter5 {
        String getValue()
    }

}
