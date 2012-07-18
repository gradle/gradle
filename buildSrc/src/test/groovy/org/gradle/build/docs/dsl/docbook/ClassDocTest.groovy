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

import org.gradle.build.docs.XmlSpecification

import org.gradle.build.docs.dsl.source.model.ClassMetaData
import org.gradle.build.docs.dsl.source.model.MethodMetaData
import org.gradle.build.docs.dsl.source.model.ParameterMetaData
import org.gradle.build.docs.dsl.source.model.PropertyMetaData
import org.gradle.build.docs.dsl.source.model.TypeMetaData
import org.gradle.build.docs.dsl.docbook.model.ClassExtensionMetaData
import org.gradle.build.docs.dsl.docbook.model.PropertyDoc
import org.gradle.build.docs.dsl.docbook.model.MethodDoc

class ClassDocTest extends XmlSpecification {
    final JavadocConverter javadocConverter = Mock()
    final DslDocModel docModel = Mock()

    def buildsMethodsForClass() {
        ClassMetaData classMetaData = classMetaData()
        MethodMetaData methodA = method('a', classMetaData)
        MethodMetaData methodB = method('b', classMetaData)
        MethodMetaData methodBOverload = method('b', classMetaData)
        MethodDoc methodAOverridden = methodDoc('a')
        MethodDoc methodC = methodDoc('c')
        ClassDoc superClass = classDoc('org.gradle.SuperClass')

        def content = parse('''
<section>
    <section><title>Methods</title>
        <table>
            <thead><tr><td>Name</td></tr></thead>
            <tr><td>a</td></tr>
            <tr><td>b</td></tr>
        </table>
    </section>
    <section><title>Properties</title><table><thead><tr>Name</tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).buildMethods()
        }

        then:
        doc.classMethods.size() == 4

        doc.classMethods[0].name == 'a'
        doc.classMethods[1].name == 'b'
        doc.classMethods[2].name == 'b'
        doc.classMethods[3].name == 'c'

        _ * classMetaData.declaredMethods >> ([methodA, methodB, methodBOverload] as Set)
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * docModel.getClassDoc('org.gradle.SuperClass') >> superClass
        _ * superClass.classMethods >> [methodC, methodAOverridden]
    }

    def buildsBlocksForClass() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData blockProperty = property('block', classMetaData)
        MethodMetaData blockMethod = method('block', classMetaData, paramTypes: [Closure.class.name])
        PropertyMetaData compositeBlockProperty = property('listBlock', classMetaData, type: new TypeMetaData('java.util.List').addTypeArg(new TypeMetaData('BlockType')))
        MethodMetaData compositeBlockMethod = method('listBlock', classMetaData, paramTypes: [Closure.class.name])
        MethodMetaData tooManyParams = method('block', classMetaData, paramTypes: ['String', 'boolean'])
        MethodMetaData notAClosure = method('block', classMetaData, paramTypes: ['String'])
        MethodMetaData noBlockProperty = method('notBlock', classMetaData, paramTypes: [Closure.class.name])
        _ * classMetaData.findProperty('block') >> blockProperty
        _ * classMetaData.findProperty('listBlock') >> compositeBlockProperty
        _ * classMetaData.declaredMethods >> [blockMethod, compositeBlockMethod, tooManyParams, notAClosure, noBlockProperty]

        def content = parse('''
<section>
    <section><title>Methods</title>
        <table>
            <thead><tr><td>Name</td></tr></thead>
            <tr><td>block</td></tr>
            <tr><td>listBlock</td></tr>
            <tr><td>notBlock</td></tr>
        </table>
    </section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td></tr></thead>
            <tr><td>block</td></tr>
            <tr><td>listBlock</td></tr>
        </table>
    </section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            def doc = new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter)
            new ClassDocPropertiesBuilder(docModel, javadocConverter, Mock(GenerationListener)).build(doc)
            doc.buildMethods()
        }

        then:
        doc.classProperties.size() == 2
        doc.classProperties[0].name == 'block'
        doc.classProperties[1].name == 'listBlock'

        doc.classMethods.size() == 3

        doc.classBlocks.size() == 2
        doc.classBlocks[0].name == 'block'
        doc.classBlocks[0].type.signature == 'org.gradle.Type'
        !doc.classBlocks[0].multiValued

        doc.classBlocks[1].name == 'listBlock'
        doc.classBlocks[1].type.signature == 'BlockType'
        doc.classBlocks[1].multiValued
    }

    def buildsExtensionsForClassMixins() {
        ClassMetaData classMetaData = classMetaData()
        ClassExtensionMetaData extensionMetaData = new ClassExtensionMetaData('org.gradle.Class')
        extensionMetaData.addMixin('a', 'org.gradle.ExtensionA1')
        extensionMetaData.addMixin('a', 'org.gradle.ExtensionA2')
        extensionMetaData.addMixin('b', 'org.gradle.ExtensionB')
        ClassDoc extensionA1 = classDoc('org.gradle.ExtensionA1')
        ClassDoc extensionA2 = classDoc('org.gradle.ExtensionA2')
        ClassDoc extensionB = classDoc('org.gradle.ExtensionB')
        _ * docModel.getClassDoc('org.gradle.ExtensionA1') >> extensionA1
        _ * docModel.getClassDoc('org.gradle.ExtensionA2') >> extensionA2
        _ * docModel.getClassDoc('org.gradle.ExtensionB') >> extensionB

        def content = parse('''<section>
                <section><title>Properties</title>
                    <table><thead><tr><td/></tr></thead></table>
                </section>
                <section><title>Methods</title>
                    <table><thead><tr><td/></tr></thead></table>
                </section>
            </section>
        ''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, extensionMetaData, docModel, javadocConverter).buildExtensions()
        }

        then:
        doc.classExtensions.size() == 2

        doc.classExtensions[0].pluginId == 'a'
        doc.classExtensions[0].mixinClasses == [extensionA1, extensionA2] as Set

        doc.classExtensions[1].pluginId == 'b'
        doc.classExtensions[1].mixinClasses == [extensionB] as Set
    }

    def buildsExtensionsForClassExtensions() {
        ClassMetaData classMetaData = classMetaData()
        ClassExtensionMetaData extensionMetaData = new ClassExtensionMetaData('org.gradle.Class')
        extensionMetaData.addExtension('a', 'n1', 'org.gradle.ExtensionA1')
        extensionMetaData.addExtension('a', 'n2', 'org.gradle.ExtensionA2')
        extensionMetaData.addExtension('b', 'n1', 'org.gradle.ExtensionB')
        ClassDoc extensionA1 = classDoc('org.gradle.ExtensionA1')
        ClassDoc extensionA2 = classDoc('org.gradle.ExtensionA2')
        ClassDoc extensionB = classDoc('org.gradle.ExtensionB')
        _ * docModel.getClassDoc('org.gradle.ExtensionA1') >> extensionA1
        _ * docModel.isKnownType('org.gradle.ExtensionA1') >> true
        _ * docModel.getClassDoc('org.gradle.ExtensionA2') >> extensionA2
        _ * docModel.isKnownType('org.gradle.ExtensionA2') >> true
        _ * docModel.getClassDoc('org.gradle.ExtensionB') >> extensionB
        _ * docModel.isKnownType('org.gradle.ExtensionB') >> true

        def content = parse('''<section>
                <section><title>Properties</title>
                    <table><thead><tr><td/></tr></thead></table>
                </section>
                <section><title>Methods</title>
                    <table><thead><tr><td/></tr></thead></table>
                </section>
            </section>
        ''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, extensionMetaData, docModel, javadocConverter).buildExtensions()
        }

        then:
        doc.classExtensions.size() == 2

        doc.classExtensions[0].pluginId == 'a'
        doc.classExtensions[0].extensionClasses == [n1: extensionA1, n2: extensionA2]
        doc.classExtensions[0].extensionProperties.size() == 2
        doc.classExtensions[0].extensionBlocks.size() == 2

        doc.classExtensions[1].pluginId == 'b'
        doc.classExtensions[1].extensionClasses == [n1: extensionB]
        doc.classExtensions[1].extensionProperties.size() == 1
        doc.classExtensions[1].extensionBlocks.size() == 1
    }

    def classMetaData(String name = 'org.gradle.Class') {
        ClassMetaData classMetaData = Mock()
        _ * classMetaData.className >> name
        return classMetaData
    }

    def classDoc(String name = 'org.gradle.Class') {
        ClassDoc doc = Mock()
        _ * doc.name >> name
        _ * doc.toString() >> "ClassDoc '$name'"
        return doc
    }

    def property(String name, ClassMetaData classMetaData) {
        return property([:], name, classMetaData)
    }

    def property(Map<String, ?> args, String name, ClassMetaData classMetaData) {
        PropertyMetaData property = Mock()
        _ * property.name >> name
        _ * property.ownerClass >> classMetaData
        def type = args.type instanceof TypeMetaData ? args.type : new TypeMetaData(args.type ?: 'org.gradle.Type')
        _ * property.type >> type
        _ * property.signature >> "$name-signature"
        _ * javadocConverter.parse(property, !null) >> ({[parse("<para>${args.comment ?: 'comment'}</para>")]} as DocComment)
        return property
    }

    def propertyDoc(Map<String, ?> args = [:], String name) {
        return new PropertyDoc(classMetaData(), property(name, null), [parse("<para>$name comment</para>")], args.additionalValues ?: [])
    }

    def method(String name, ClassMetaData classMetaData) {
        return method([:], name, classMetaData)
    }

    def method(Map<String, ?> args, String name, ClassMetaData classMetaData) {
        MethodMetaData method = Mock()
        List<String> paramTypes = args.paramTypes ?: []
        _ * method.name >> name
        _ * method.overrideSignature >> "$name(${paramTypes.join(', ')})"
        _ * method.parameters >> paramTypes.collect {
            def param = new ParameterMetaData("p");
            param.type = new TypeMetaData(it)
            return param
        }
        _ * method.ownerClass >> classMetaData
        _ * method.returnType >> new TypeMetaData(args.returnType ?: 'ReturnType')
        _ * javadocConverter.parse(method, !null) >> ({[parse("<para>${args.comment ?: 'comment'}</para>")]} as DocComment)
        return method
    }

    def methodDoc(String name) {
        MethodDoc methodDoc = Mock()
        _ * methodDoc.name >> name
        _ * methodDoc.metaData >> method(name, null)
        _ * methodDoc.forClass(!null) >> methodDoc
        return methodDoc
    }
}
