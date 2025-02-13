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
        doc.classProperties.size() == 2
        doc.classProperties[0].name == 'a'
        doc.classProperties[1].name == 'b'

        _ * classMetaData.findProperty('b') >> propertyB
        _ * classMetaData.findProperty('a') >> propertyA
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * superDoc.classProperties >> [propertyDocC, propertyDocA]
        _ * superType1.classProperties >> [propertyDoc('d')]
        _ * superType2.classProperties >> [propertyDoc('d'), propertyDoc('e')]
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
