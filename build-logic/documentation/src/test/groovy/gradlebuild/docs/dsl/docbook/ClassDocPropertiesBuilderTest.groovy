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
import gradlebuild.docs.dsl.docbook.model.ClassDoc
import gradlebuild.docs.dsl.docbook.model.ExtraAttributeDoc
import gradlebuild.docs.dsl.docbook.model.PropertyDoc
import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.PropertyMetaData
import gradlebuild.docs.dsl.source.model.TypeMetaData

class ClassDocPropertiesBuilderTest extends XmlSpecification {
    final JavadocConverter javadocConverter = Mock()
    final GenerationListener listener = Mock()
    final ClassDocPropertiesBuilder builder = new ClassDocPropertiesBuilder(javadocConverter, listener)

    def buildsPropertiesForClass() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData propertyA = property('a', classMetaData, comment: 'prop a')
        PropertyMetaData propertyB = property('b', classMetaData, comment: 'prop b')
        ClassDoc superDoc = classDoc('org.gradle.SuperClass')
        PropertyDoc propertyDocA = propertyDoc('a')
        PropertyDoc propertyDocC = propertyDoc('c')

        ClassDoc superType1 = classDoc("org.gradle.SuperType1")
        ClassDoc superType2 = classDoc("org.gradle.SuperType2")

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td></tr></thead>
            <tr><td>b</td></tr>
            <tr><td>a</td></tr>
        </table>
    </section>
    <section><title>Methods</title><table><thead><tr></tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            def doc = new ClassDoc('org.gradle.Class', content, document, classMetaData, null)
            doc.superClass = superDoc
            doc.interfaces << superType1
            doc.interfaces << superType2
            builder.build(doc)
            return doc
        }

        then:
        doc.classProperties.size() == 5
        doc.classProperties[0].name == 'a'
        doc.classProperties[1].name == 'b'
        doc.classProperties[2].name == 'c'
        doc.classProperties[3].name == 'd'
        doc.classProperties[4].name == 'e'

        _ * classMetaData.findProperty('b') >> propertyB
        _ * classMetaData.findProperty('a') >> propertyA
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * superDoc.classProperties >> [propertyDocC, propertyDocA]
        _ * superType1.classProperties >> [propertyDoc('d')]
        _ * superType2.classProperties >> [propertyDoc('d'), propertyDoc('e')]
    }

    def canAttachAdditionalValuesToProperty() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData propertyA = property('a', classMetaData, comment: 'prop a')
        PropertyMetaData propertyB = property('b', classMetaData, comment: 'prop b')
        ClassDoc superDoc = classDoc()
        ClassDoc superType = classDoc("org.gradle.SuperType")
        ExtraAttributeDoc inheritedValue = new ExtraAttributeDoc(parse('<td>inherited</td>'), parse('<td>inherited</td>'))
        ExtraAttributeDoc overriddenValue = new ExtraAttributeDoc(parse('<td>general value</td>'), parse('<td>general</td>'))
        PropertyDoc inheritedPropertyA = propertyDoc('a', additionalValues: [inheritedValue, overriddenValue])
        PropertyDoc inheritedPropertyB = propertyDoc('b', additionalValues: [inheritedValue, overriddenValue])
        PropertyDoc inheritedPropertyC = propertyDoc('c', additionalValues: [inheritedValue, overriddenValue])

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td><td>inherited</td><td>added</td><td>overridden <overrides>general value</overrides></td></tr></thead>
            <tr><td>a</td><td>specific1</td><td>specific2</td><td>specific3</td></tr>
            <tr><td>b</td><td></td><td/><td/></tr>
        </table>
    </section>
    <section><title>Methods</title><table><thead><tr></tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            def doc = new ClassDoc('org.gradle.Class', content, document, classMetaData, null)
            doc.superClass = superDoc
            doc.interfaces << superType
            builder.build(doc)
            return doc
        }

        then:
        doc.classProperties.size() == 3

        def prop = doc.classProperties[0]
        prop.name == 'a'
        prop.additionalValues.size() == 3
        format(prop.additionalValues[0].title) == 'inherited'
        format(prop.additionalValues[0].value) == 'specific1'
        format(prop.additionalValues[1].title) == 'overridden'
        format(prop.additionalValues[1].value) == 'specific3'
        format(prop.additionalValues[2].title) == 'added'
        format(prop.additionalValues[2].value) == 'specific2'

        def prop2 = doc.classProperties[1]
        prop2.name == 'b'
        prop2.additionalValues.size() == 2
        format(prop2.additionalValues[0].title) == 'inherited'
        format(prop2.additionalValues[0].value) == 'inherited'
        format(prop2.additionalValues[1].title) == 'overridden'
        format(prop2.additionalValues[1].value) == 'general'

        def prop3 = doc.classProperties[2]
        prop3.name == 'c'
        prop3.additionalValues.size() == 2
        format(prop3.additionalValues[0].title) == 'inherited'
        format(prop3.additionalValues[0].value) == 'inherited'
        format(prop3.additionalValues[1].title) == 'overridden'
        format(prop3.additionalValues[1].value) == 'general'

        _ * classMetaData.findProperty('b') >> propertyB
        _ * classMetaData.findProperty('a') >> propertyA
        _ * classMetaData.superClassName >> 'org.gradle.SuperType'
        _ * superDoc.classProperties >> [inheritedPropertyA, inheritedPropertyB]
        _ * superType.classProperties >> [inheritedPropertyC]
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
}
