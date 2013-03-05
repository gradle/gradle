/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.docbook

import org.gradle.build.docs.dsl.source.TypeNameResolver
import org.gradle.build.docs.XmlSpecification
import org.gradle.build.docs.dsl.source.model.ClassMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository
import org.gradle.build.docs.dsl.source.model.MethodMetaData

class JavadocLinkConverterTest extends XmlSpecification {
    final LinkRenderer linkRenderer = Mock()
    final TypeNameResolver nameResolver = Mock()
    final ClassMetaData classMetaData = Mock()
    final ClassMetaDataRepository<ClassMetaData> repository = Mock()
    final GenerationListener listener = Mock()
    final JavadocLinkConverter converter = new JavadocLinkConverter(document, nameResolver, linkRenderer, repository)

    def convertsClassNameToLink() {
        when:
        def link = converter.resolve('someName', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
        _ * nameResolver.resolve('someName', classMetaData) >> 'org.gradle.SomeClass'
        _ * linkRenderer.link({it.name == 'org.gradle.SomeClass'}, listener) >> parse('<someLinkElement/>')
    }

    def convertsFullyQualifiedClassNameToLink() {
        when:
        def link = converter.resolve('org.gradle.SomeClass', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
        _ * nameResolver.resolve('org.gradle.SomeClass', classMetaData) >> 'org.gradle.SomeClass'
        _ * linkRenderer.link({it.name == 'org.gradle.SomeClass'}, listener) >> parse('<someLinkElement/>')
    }

    def resolvesUnknownFullyQualifiedClassName() {
        when:
        def link = converter.resolve('org.gradle.SomeClass', classMetaData, listener)

        then:
        format(link) == '''<UNHANDLED-LINK>org.gradle.SomeClass</UNHANDLED-LINK>'''
    }

    def convertsClassAndMethodNameToLink() {
        ClassMetaData targetClass = Mock()
        MethodMetaData method = method('someName')
        _ * nameResolver.resolve('SomeClass', classMetaData) >> 'org.gradle.SomeClass'
        _ * repository.find('org.gradle.SomeClass') >> targetClass
        _ * targetClass.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('SomeClass#someName', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodNameToLink() {
        MethodMetaData method = method('someName')
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodSignatureToLink() {
        MethodMetaData method = method('someName', signature: 'someName(org.gradle.SomeClass, java.lang.Object)')
        _ * nameResolver.resolve('SomeClass', classMetaData) >>'org.gradle.SomeClass'
        _ * nameResolver.resolve('Object', classMetaData) >>'java.lang.Object'
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName(SomeClass, Object)', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodSignatureWithNoParamsToLink() {
        MethodMetaData method = method('someName', signature: 'someName()')
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName()', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodSignatureWithArrayTypeToLink() {
        MethodMetaData method = method('someName', signature: 'someName(org.gradle.SomeClass[], java.lang.Object)')
        _ * nameResolver.resolve('SomeClass', classMetaData) >>'org.gradle.SomeClass'
        _ * nameResolver.resolve('Object', classMetaData) >>'java.lang.Object'
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName(SomeClass[], Object)', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodNameWithLabelToLink() {
        MethodMetaData method = method('someName')
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName this is the label.', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsMethodSignatureWithLabelToLink() {
        MethodMetaData method = method('someName', signature: 'someName(org.gradle.SomeClass, java.lang.Object)')
        _ * nameResolver.resolve('SomeClass', classMetaData) >>'org.gradle.SomeClass'
        _ * nameResolver.resolve('Object', classMetaData) >>'java.lang.Object'
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve('#someName(SomeClass, Object) this is a label', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
    }

    def convertsEnumConstantLinkToLiteralValue() {
        ClassMetaData otherClass = Mock()

        when:
        def link = converter.resolve('SomeName#SOME_ENUM_VALUE', classMetaData, listener)

        then:
        format(link) == '<literal>TargetName.SOME_ENUM_VALUE</literal>'
        _ * nameResolver.resolve('SomeName', classMetaData) >> 'org.gradle.SomeName'
        _ * repository.find('org.gradle.SomeName') >> otherClass
        _ * otherClass.enum >> true
        _ * otherClass.declaredEnumConstants >> ["SOME_ENUM_VALUE", "OTHER_ENUM"]
        _ * otherClass.simpleName >> "TargetName"
    }

    def convertsValueLinkToLiteralValue() {
        ClassMetaData otherClass = Mock()

        when:
        def link = converter.resolveValue('SomeName#someField', classMetaData, listener)

        then:
        format(link) == '<literal>value</literal>'
        _ * nameResolver.resolve('SomeName', classMetaData) >> 'org.gradle.SomeName'
        _ * repository.find('org.gradle.SomeName') >> otherClass
        _ * otherClass.constants >> [someField: 'value']
    }

    def convertsValueLinkInSameClassToLiteralValue() {
        when:
        def link = converter.resolveValue('#someField', classMetaData, listener)

        then:
        format(link) == '<literal>value</literal>'
        _ * classMetaData.constants >> [someField: 'value']
    }

    private MethodMetaData method(Map<String, ?> args = [:], String name) {
        def MethodMetaData method = Mock()
        _ * method.name >> name
        _ * method.overrideSignature >> args.signature
        return method
    }
}

