/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.source.model

import spock.lang.Specification

class MethodMetaDataTest extends Specification {
    final ClassMetaData owner = Mock()
    final MethodMetaData method = new MethodMetaData('method', owner)

    def formatsSignature() {
        method.returnType = new TypeMetaData('ReturnType')
        method.addParameter('param1', new TypeMetaData('ParamType'))
        method.addParameter('param2', new TypeMetaData('ParamType2'))

        expect:
        method.signature == 'ReturnType method(ParamType param1, ParamType2 param2)'
        method.overrideSignature == 'method(ParamType, ParamType2)'
    }

    def formatsOverrideSignatureUsingRawParameterTypes() {
        method.returnType = new TypeMetaData('ReturnType')
        method.addParameter('param', new TypeMetaData('ParamType').addTypeArg(new TypeMetaData("Type1")))
        method.addParameter('param2', new TypeMetaData('ParamType2'))

        expect:
        method.signature == 'ReturnType method(ParamType<Type1> param, ParamType2 param2)'
        method.overrideSignature == 'method(ParamType, ParamType2)'
    }

    def formatsOverrideSignatureForVarargsParameter() {
        method.returnType = new TypeMetaData('ReturnType')
        method.addParameter('param', new TypeMetaData('ParamType'))
        method.addParameter('param2', new TypeMetaData('ParamType2').setVarargs())

        expect:
        method.signature == 'ReturnType method(ParamType param, ParamType2... param2)'
        method.overrideSignature == 'method(ParamType, ParamType2[])'
    }

    def locatesOverriddenMethodInSuperClass() {
        ClassMetaData superClassMetaData = Mock()
        MethodMetaData overriddenMethod = Mock()

        when:
        def m = method.overriddenMethod

        then:
        m == overriddenMethod
        _ * owner.superClass >> superClassMetaData
        _ * owner.interfaces >> []
        1 * superClassMetaData.findDeclaredMethod('method()') >> overriddenMethod
    }

    def locatesOverriddenMethodInDirectlyImplementedInterface() {
        ClassMetaData interfaceMetaData = Mock()
        MethodMetaData overriddenMethod = Mock()

        when:
        def m = method.overriddenMethod

        then:
        m == overriddenMethod
        _ * owner.superClass >> null
        _ * owner.interfaces >> [interfaceMetaData]
        1 * interfaceMetaData.findDeclaredMethod('method()') >> overriddenMethod
    }

    def locatesOverriddenMethodInAncestorClass() {
        ClassMetaData superClassMetaData = Mock()
        ClassMetaData ancestorClassMetaData = Mock()
        MethodMetaData overriddenMethod = Mock()

        when:
        def m = method.overriddenMethod

        then:
        m == overriddenMethod
        _ * owner.superClass >> superClassMetaData
        _ * owner.interfaces >> []
        1 * superClassMetaData.findDeclaredMethod('method()') >> null
        _ * superClassMetaData.superClass >> ancestorClassMetaData
        _ * superClassMetaData.interfaces >> []
        1 * ancestorClassMetaData.findDeclaredMethod('method()') >> overriddenMethod
    }

    def locatesOverriddenMethodInInterfaceOfAncestorClass() {
        ClassMetaData superClassMetaData = Mock()
        ClassMetaData interfaceMetaData = Mock()
        MethodMetaData overriddenMethod = Mock()

        when:
        def m = method.overriddenMethod

        then:
        m == overriddenMethod
        _ * owner.superClass >> superClassMetaData
        _ * owner.interfaces >> []
        1 * superClassMetaData.findDeclaredMethod('method()') >> null
        _ * superClassMetaData.superClass >> null
        _ * superClassMetaData.interfaces >> [interfaceMetaData]
        1 * interfaceMetaData.findDeclaredMethod('method()') >> overriddenMethod
    }

    def hasNoOverriddenMethodWhenNoSuperClass() {
        when:
        def m = method.overriddenMethod

        then:
        m == null
        _ * owner.superClass >> null
        _ * owner.interfaces >> []
    }

    def hasNoOverriddenMethodWhenMethodDoesNotOverrideMethodInSuperClass() {
        ClassMetaData superClassMetaData = Mock()

        when:
        def m = method.overriddenMethod

        then:
        m == null
        _ * owner.superClass >> superClassMetaData
        _ * owner.interfaces >> []
        1 * superClassMetaData.findDeclaredMethod('method()') >> null
        _ * superClassMetaData.superClass >> null
        _ * superClassMetaData.interfaces >> []
    }

    def "is deprecated when @Deprecated is attached to method"() {
        def notDeprecated = new MethodMetaData('param', owner)
        def deprecated = new MethodMetaData('param', owner)
        deprecated.addAnnotationTypeName(Deprecated.class.name)

        expect:
        !notDeprecated.deprecated
        deprecated.deprecated
    }

    def "is incubating when @Incubating is attached to method"() {
        def notIncubating = new MethodMetaData('param', owner)
        def incubating = new MethodMetaData('param', owner)
        incubating.addAnnotationTypeName("org.gradle.api.Incubating")

        expect:
        !notIncubating.incubating
        incubating.incubating
    }

}
