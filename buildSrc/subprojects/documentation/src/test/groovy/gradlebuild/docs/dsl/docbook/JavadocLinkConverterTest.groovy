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
package gradlebuild.docs.dsl.docbook

import gradlebuild.docs.XmlSpecification
import gradlebuild.docs.dsl.source.TypeNameResolver
import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.EnumConstantMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData
import gradlebuild.docs.model.ClassMetaDataRepository

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
        def link = converter.resolve(input, classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'

        where:
        input << [
                '#someName(SomeClass,Object)',
                '#someName(SomeClass, Object)',
                '#someName(SomeClass,\tObject)',
                '#someName  (  \t SomeClass ,\tObject\t ) '
        ]
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

    def convertsMethodSignatureWithVarargsTypeToLink() {
        MethodMetaData method = method('someName', signature: 'someName(org.gradle.SomeClass[])')
        _ * nameResolver.resolve('SomeClass', classMetaData) >>'org.gradle.SomeClass'
        _ * classMetaData.declaredMethods >> ([method] as Set)
        _ * linkRenderer.link(method, listener) >> parse('<someLinkElement/>')

        when:
        def link = converter.resolve(input, classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'

        where:
        input << [
                '#someName(SomeClass[])',
                '#someName(SomeClass [] )',
                '#someName(SomeClass...)',
                '#someName(SomeClass ... )'
        ]
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
        ClassMetaData enumClass = Mock()
        EnumConstantMetaData enumConstant = new EnumConstantMetaData("SOME_ENUM_VALUE", enumClass)

        when:
        def link = converter.resolve('SomeName#SOME_ENUM_VALUE', classMetaData, listener)

        then:
        format(link) == '<someLinkElement/>'
        _ * nameResolver.resolve('SomeName', classMetaData) >> 'org.gradle.SomeName'
        _ * repository.find('org.gradle.SomeName') >> enumClass
        _ * enumClass.enum >> true
        _ * enumClass.getEnumConstant("SOME_ENUM_VALUE") >> enumConstant
        _ * linkRenderer.link(enumConstant, listener) >> parse('<someLinkElement/>')
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

