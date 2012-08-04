/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.reflect;

import spock.lang.Specification
import org.gradle.internal.UncheckedException

class JavaReflectionUtilTest extends Specification {
    def myProperties = new MyProperties()

    def "read property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "myValue"
    }

    def "write property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myProperty", "otherValue")

        then:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "otherValue"
    }

    def "read boolean property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == true
    }

    def "write boolean property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myBooleanProperty", false)

        then:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == false
    }

    def "read property that doesn't exist"() {
        when:
        JavaReflectionUtil.readProperty(myProperties, "unexisting")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    def "write property that doesn't exist"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "unexisting", "someValue")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    static class MyProperties {
        private String myProp = "myValue"
        private boolean myBooleanProp = true

        String getMyProperty() {  myProp }
        void setMyProperty(String value) { myProp = value }

        boolean isMyBooleanProperty() { myBooleanProp }
        void setMyBooleanProperty(boolean value) { myBooleanProp = value }
    }
}

