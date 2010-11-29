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

import org.gradle.build.docs.dsl.XmlSpecification
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData
import groovy.xml.dom.DOMCategory
import org.gradle.build.docs.BuildableDOMCategory

class ClassDocTest extends XmlSpecification {
    final JavadocConverter javadocConverter = Mock()

    def mixesTypeAndDescriptionIntoPropertyTable() {
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
    <section><title>Methods</title>
        <table>
            <thead><td>Name</td><td>Extra column</td></thead>
        </table>
    </section>
</section>
''')

        when:
        ClassDoc doc = withCategories { new ClassDoc('org.gradle.Class', content, document, classMetaData, null, null, javadocConverter) }

        then:
        format { doc.propertiesTable } == '''<table><title>Properties - Class</title>
            <thead><tr><td>Name</td><td>Description</td><td>Type</td><td>Extra column</td></tr></thead>
            <tr><td><literal>propName</literal></td><td><prop-comment/></td><td><apilink class="org.gradle.Type"/> (read-only)</td><td>some value</td></tr>
        </table>'''

        _ * classMetaData.classProperties >> [propName: propertyMetaData]
        _ * propertyMetaData.type >> 'org.gradle.Type'
        _ * propertyMetaData.rawCommentText >> 'a property'
        _ * classMetaData.rawCommentText >> 'a class'
        _ * javadocConverter.parse('a property', propertyMetaData, classMetaData) >> ({[document.createElement('prop-comment')]} as DocComment)
        _ * javadocConverter.parse('a class',  classMetaData) >> ({[document.createElement('class-comment')]} as DocComment)
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
