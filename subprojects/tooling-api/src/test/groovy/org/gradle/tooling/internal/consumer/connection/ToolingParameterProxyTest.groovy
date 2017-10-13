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

import org.gradle.testing.internal.util.Specification

import java.lang.reflect.Proxy

class ToolingParameterProxyTest extends Specification {

    def "returns parameter valid when well defined"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(ValidParameter)

        then:
        assert isValid
    }

    def "returns parameter invalid when not a getter or setter"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(InvalidParameter1)

        then:
        assert !isValid
    }

    def "returns parameter invalid when setter not correct"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(InvalidParameter2)

        then:
        assert !isValid
    }

    def "returns parameter invalid when setter and getter have different types"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(InvalidParameter3)

        then:
        assert !isValid
    }

    def "returns parameter invalid when no getter for setter"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(InvalidParameter4)

        then:
        assert !isValid
    }

    def "returns parameter invalid when no setter for getter"() {
        when:
        boolean isValid = ToolingParameterProxy.isValid(InvalidParameter5)

        then:
        assert !isValid
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
}
