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

import groovy.xml.dom.DOMCategory
import org.gradle.build.docs.BuildableDOMCategory
import org.gradle.build.docs.dsl.XmlSpecification
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.MethodMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.gradle.build.docs.dsl.model.TypeMetaData

class ClassDocTest extends XmlSpecification {
    final JavadocConverter javadocConverter = Mock()
    final DslDocModel docModel = Mock()

    def mixesPropertyTypeAndDescriptionIntoPropertyTable() {
        ClassMetaData classMetaData = Mock()
        PropertyMetaData propertyMetaData = Mock()

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
            <tr><td>propName</td><td>some value</td></tr>
        </table>
    </section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).mergeProperties()
        }

        then:
        format { doc.propertiesTable } == '''<table><title>Properties - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Extra column</td></tr></thead>
            <tr><td><link linkend="propSignature"><literal>propName</literal></link></td><td>propName comment</td><td>some value</td></tr>
        </table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
        _ * propertyMetaData.type >> new TypeMetaData('org.gradle.Type')
        _ * propertyMetaData.signature >> 'propSignature'
        _ * javadocConverter.parse(propertyMetaData) >> ({[document.createTextNode('propName comment')]} as DocComment)
    }

    def mixesInheritedPropertiesIntoPropertyTable() {
        ClassMetaData classMetaData = Mock()
        PropertyMetaData propertyMetaData = Mock()
        PropertyMetaData inherited2MetaData = Mock()
        ClassDoc superClassDoc = Mock()

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
            <tr><td>propName</td><td>some value</td></tr>
            <tr><td>inherited2</td><td>adds extra column</td></tr>
        </table>
    </section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).mergeProperties()
        }

        then:
        format { doc.propertiesTable } == '''<table><title>Properties - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Extra column</td></tr></thead>
            <tr><td><link linkend="propSignature"><literal>propName</literal></link></td><td>propName comment</td><td>some value</td></tr>
            <tr><td><link linkend="inherited2Signature"><literal>inherited2</literal></link></td><td>inherited2 comment</td><td>adds extra column</td></tr>
        <tr><td>inherited1</td><td/><td/></tr></table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
        _ * classMetaData.findProperty('inherited2') >> inherited2MetaData
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * propertyMetaData.type >> new TypeMetaData('org.gradle.Type')
        _ * propertyMetaData.signature >> 'propSignature'
        _ * inherited2MetaData.type >> new TypeMetaData('org.gradle.Type3')
        _ * inherited2MetaData.signature >> 'inherited2Signature'
        _ * docModel.getClassDoc('org.gradle.SuperClass') >> superClassDoc
        _ * javadocConverter.parse(propertyMetaData) >> ({[document.createTextNode('propName comment')]} as DocComment)
        _ * javadocConverter.parse(inherited2MetaData) >> ({[document.createTextNode('inherited2 comment')]} as DocComment)
        _ * superClassDoc.propertiesTable >> parse('<table><tr><td>inherited1</td></tr><tr><td>inherited2</td></tr></table>')
    }

    def mergesMethodSignatureAndDescriptionIntoMethodsTable() {
        ClassMetaData classMetaData = Mock()
        MethodMetaData method1 = Mock()
        MethodMetaData method2 = Mock()

        def content = parse('''
<section>
    <section><title>Methods</title>
        <table>
            <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
            <tr><td>methodName</td><td>some value</td></tr>
        </table>
    </section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).mergeMethods()
        }

        then:
        format { doc.methodsTable } == '''<table><title>Methods - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Extra column</td></tr></thead>
            <tr><td><link linkend="method-signature"><literal>methodName</literal></link></td><td>method description</td><td>some value</td></tr><tr><td><link linkend="overloaded-signature"><literal>methodName</literal></link></td><td>overloaded description</td></tr>
        </table>'''

        _ * classMetaData.declaredMethods >> ([method1, method2] as LinkedHashSet)
        _ * method1.name >> 'methodName'
        _ * method1.signature >> 'method-signature'
        _ * method1.returnType >> new TypeMetaData('ReturnType')
        _ * method2.name >> 'methodName'
        _ * method2.signature >> 'overloaded-signature'
        _ * method2.returnType >> new TypeMetaData('ReturnType2')
        _ * javadocConverter.parse(method1) >> ({[document.createTextNode('method description')]} as DocComment)
        _ * javadocConverter.parse(method2) >> ({[document.createTextNode('overloaded description')]} as DocComment)
    }

    def format(Closure cl) {
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                return format(cl.call())
            }
        }
    }

    def withCategories(Closure cl) {
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                cl.call()
            }
        }
    }
}
