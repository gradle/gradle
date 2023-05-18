/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath.intercept

import spock.lang.Specification

class InterceptScopeTest extends Specification {
    def "#firstScope with the same name are equal"() {
        expect:
        firstScope == secondScope

        where:
        firstScope                                        | secondScope
        InterceptScope.readsOfPropertiesNamed("property") | InterceptScope.readsOfPropertiesNamed("property")
        InterceptScope.methodsNamed("method")             | InterceptScope.methodsNamed("method")
    }

    def "#firstScope and #secondScope with different names are not equal"() {
        expect:
        firstScope != secondScope

        where:
        firstScope                                         | secondScope
        InterceptScope.readsOfPropertiesNamed("property1") | InterceptScope.readsOfPropertiesNamed("property2")
        InterceptScope.methodsNamed("method1")             | InterceptScope.methodsNamed("method2")
    }

    def "method and property scopes with the same name are not equal"() {
        when:
        InterceptScope methodScope = InterceptScope.methodsNamed("name")
        InterceptScope propertyScope = InterceptScope.readsOfPropertiesNamed("name")

        then:
        methodScope != propertyScope
    }

    def "constructor scopes of the same class are equal"() {
        expect:
        InterceptScope.constructorsOf(String) == InterceptScope.constructorsOf(String)
    }

    def "constructor scopes of different classes are not equal"() {
        expect:
        InterceptScope.constructorsOf(Integer) != InterceptScope.constructorsOf(Long)
    }
}
