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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.api.Action
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.internal.adapter.ObjectGraphAdapter
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.adapter.ViewBuilder
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.InternalProtocolInterface
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.model.Element
import org.gradle.tooling.model.gradle.GradleBuild
import spock.lang.Specification

class BuildControllerAdapterTest extends Specification {
    def internalController = Mock(InternalBuildControllerAdapter)
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
    def controller = new BuildControllerAdapter(adapter, internalController, mapping, new File("root"))

    def "unpacks unsupported model exception"() {
        def failure = new RuntimeException()

        given:
        _ * internalController.getModel(null, _, null) >> { throw new InternalUnsupportedModelException().initCause(failure) }

        when:
        controller.getModel(String)

        then:
        def e = thrown UnknownModelException
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

    def "error when parameterType or parameterInitializer null"() {
        when:
        controller.getModel(GradleBuild, null, new Action() {
            @Override
            void execute(Object o) {}
        })

        then:
        def e1 = thrown NullPointerException
        e1.message == "parameterType and parameterInitializer both need to be set for a parametrized model request."

        when:
        controller.getModel(GradleBuild, ValidParameter, null)

        then:
        def e2 = thrown NullPointerException
        e2.message == "parameterType and parameterInitializer both need to be set for a parametrized model request."
    }

    def "error when invalid parameter type is used"() {
        when:
        controller.getModel(GradleBuild, InvalidParameter, new Action() {
            @Override
            void execute(Object o) {}
        })

        then:
        def e1 = thrown IllegalArgumentException
        e1.message == "org.gradle.tooling.internal.consumer.connection.BuildControllerAdapterTest\$InvalidParameter is not a valid parameter type. It must be an interface."
    }

    interface ValidParameter {
        void setValue(String value)
        String getValue()
        void setBooleanValue(boolean booleanValue)
        boolean isBooleanValue()
    }

    class InvalidParameter {
        String foo
    }
}
