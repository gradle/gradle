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

package org.gradle.model.internal.core.rule.describe

import org.gradle.model.internal.method.WeaklyTypeReferencingMethod
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class MethodModelRuleDescriptorTest extends Specification {

    def "check description"() {
        when:
        def sb = new StringBuilder()
        MethodModelRuleDescriptor.of(weakMethod(getClass(), method)).describeTo(sb)

        then:
        sb.toString() == ModelType.of(getClass()).displayName + "#" + desc

        where:
        method        | desc
        "noArgs"      | 'noArgs()'
        "oneArg"      | 'oneArg(String)'
        "twoArgs"     | 'twoArgs(String, String)'
        "genericArgs" | 'genericArgs(List<String>, Map<Integer, List<String>>)'
    }

    def "inner classes are described"() {
        when:
        def sb = new StringBuilder()
        MethodModelRuleDescriptor.of(weakMethod(Outer.Inner, "noArgs")).describeTo(sb)

        then:
        sb.toString() == 'MethodModelRuleDescriptorTest.Outer.Inner#noArgs()'
    }

    private WeaklyTypeReferencingMethod weakMethod(Class type, String name) {
        def declaringType = ModelType.of(type)
        def method = type.getDeclaredMethods().find { it.name == name }
        WeaklyTypeReferencingMethod.of(declaringType, ModelType.of(method.returnType), method)
    }

    def noArgs() {}

    def oneArg(String s1) {}

    def twoArgs(String s1, String s2) {}

    def genericArgs(List<String> list, Map<Integer, List<String>> map) {}

    class Outer {
        static class Inner {
            def noArgs() {}
        }
    }
}
