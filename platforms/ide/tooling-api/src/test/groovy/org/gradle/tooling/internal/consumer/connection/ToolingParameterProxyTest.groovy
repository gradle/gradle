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

import spock.lang.Specification

import java.lang.reflect.Proxy

class ToolingParameterProxyTest extends Specification {

    def "returns parameter valid when well defined"() {
        when:
        ToolingParameterProxy.validateParameter(ValidParameter)

        then:
        noExceptionThrown()
    }

    def "returns parameter invalid when not a getter or setter"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter1)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter1 is not a valid parameter type. Method notASetterOrGetter is neither a setter nor a getter."
    }

    def "returns parameter invalid when setter not correct"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter2)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter2 is not a valid parameter type. Method setValue is neither a setter nor a getter."
    }

    def "returns parameter invalid when setter and getter have different types"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter3)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter3 is not a valid parameter type. Setter and getter for property value have non corresponding types."
    }

    def "returns parameter invalid when no getter for setter"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter4)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter4 is not a valid parameter type. It contains a different number of getters and setters."
    }

    def "returns parameter invalid when no setter for getter"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter5)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter5 is not a valid parameter type. It contains a different number of getters and setters."
    }

    def "returns parameter invalid when it is not an interface"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter6)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter6 is not a valid parameter type. It must be an interface."
    }

    def "returns parameter invalid when more than one getter for property"() {
        when:
        ToolingParameterProxy.validateParameter(InvalidParameter7)

        then:
        IllegalArgumentException e = thrown()
        e.message == "org.gradle.tooling.internal.consumer.connection.ToolingParameterProxyTest\$InvalidParameter7 is not a valid parameter type. More than one getter for property value was found."
    }

    def "getter gets what setter sets"() {
        Class<?>[] classes = [ValidParameter]
        when:
        def parameter = Proxy.newProxyInstance(ValidParameter.getClassLoader(), classes, new ToolingParameterProxy())

        then:
        assert parameter instanceof ValidParameter

        when:
        parameter.setBooleanValue(true)
        parameter.setValue("myValue")

        then:
        assert parameter.isBooleanValue()
        assert parameter.getValue() == "myValue"
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

    class InvalidParameter6 {
        String value
        String getValue() {
            return value
        }
        void setValue(String value) {
            this.value = value
        }
    }

    interface InvalidParameter7 {
        boolean getValue()
        boolean isValue()
        void setValue(boolean value)
    }
}
