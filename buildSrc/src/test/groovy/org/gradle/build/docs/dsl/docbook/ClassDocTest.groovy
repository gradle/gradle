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
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.gradle.build.docs.dsl.model.MethodMetaData

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
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, null, javadocConverter).mergeProperties()
        }

        then:
        format { doc.propertiesTable } == '''<table><title>Properties - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Type</td><td>Extra column</td></tr></thead>
            <tr><td><literal>propName</literal></td><td>propName comment</td><td><apilink class="org.gradle.Type"/> (read-only)</td><td>some value</td></tr>
        </table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
        _ * propertyMetaData.type >> 'org.gradle.Type'
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
            <thead><tr><td>Name</td><td>Description</td><td>Type</td><td>Extra column</td></tr></thead>
            <tr><td><literal>propName</literal></td><td>propName comment</td><td><apilink class="org.gradle.Type"/> (read-only)</td><td>some value</td></tr>
            <tr><td><literal>inherited2</literal></td><td>inherited2 comment</td><td><apilink class="org.gradle.Type3"/> (read-only)</td><td>adds extra column</td></tr>
        <tr><td>inherited1</td><td/><td/><td/></tr></table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
        _ * classMetaData.findProperty('inherited2') >> inherited2MetaData
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * propertyMetaData.type >> 'org.gradle.Type'
        _ * inherited2MetaData.type >> 'org.gradle.Type3'
        _ * docModel.getClassDoc('org.gradle.SuperClass') >> superClassDoc
        _ * javadocConverter.parse(propertyMetaData) >> ({[document.createTextNode('propName comment')]} as DocComment)
        _ * javadocConverter.parse(inherited2MetaData) >> ({[document.createTextNode('inherited2 comment')]} as DocComment)
        _ * superClassDoc.propertiesTable >> parse('<table><tr><td>inherited1</td></tr><tr><td>inherited2</td></tr></table>')
    }
    
    def mergesMethodSignatureAndDescriptionIntoMethodsTable() {
        ClassMetaData classMetaData = Mock()
        MethodMetaData methodMetaData = Mock()

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
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, null, javadocConverter).mergeMethods()
        }

        then:
        format { doc.methodsTable } == '''<table><title>Methods - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Signature</td><td>Extra column</td></tr></thead>
            <tr><td><literal>methodName</literal></td><td>method description</td><td><literal>method-signature</literal></td><td>some value</td></tr>
        </table>'''

        _ * classMetaData.declaredMethods >> ([methodMetaData] as Set)
        _ * methodMetaData.name >> 'methodName'
        _ * methodMetaData.signature >> 'method-signature'
        _ * javadocConverter.parse(methodMetaData) >> ({[document.createTextNode('method description')]} as DocComment)
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
