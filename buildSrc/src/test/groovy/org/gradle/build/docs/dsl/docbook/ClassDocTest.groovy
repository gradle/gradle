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

    def buildsPropertiesForClass() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData propertyA = property('a', classMetaData, comment: 'prop a')
        PropertyMetaData propertyB = property('b', classMetaData, comment: 'prop b')
        ClassDoc superDoc = classDoc()
        PropertyDoc propertyDocA = propertyDoc('a')
        PropertyDoc propertyDocC = propertyDoc('c')

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
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).buildProperties()
        }

        then:
        doc.classProperties.size() == 3
        doc.classProperties[0].name == 'a'
        doc.classProperties[1].name == 'b'
        doc.classProperties[2] == propertyDocC

        _ * classMetaData.findProperty('b') >> propertyB
        _ * classMetaData.findProperty('a') >> propertyA
        _ * classMetaData.superClassName >> 'org.gradle.SuperType'
        _ * docModel.getClassDoc('org.gradle.SuperType') >> superDoc
        _ * superDoc.getClassProperties() >> [propertyDocC, propertyDocA]
    }

    def mixesPropertyTypeAndDescriptionIntoPropertyTable() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData propertyMetaData = property('propName', classMetaData, comment: 'propName comment')

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
            <tr><td>propName</td><td>some value</td></tr>
        </table>
    </section>
    <section><title>Methods</title><table><thead><tr></tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).buildProperties().mergeProperties()
        }

        then:
        formatTree { doc.propertiesTable } == '''<table>
    <title>Properties - Class</title>
    <thead>
        <tr>
            <td>Name</td>
            <td>Description</td>
            <td>Extra column</td>
        </tr>
    </thead>
    <tr>
        <td>
            <link linkend="org.gradle.Class:propName">
                <literal>propName</literal>
            </link>
        </td>
        <td>
            <para>propName comment</para>
        </td>
        <td>some value</td>
    </tr>
</table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
    }

    def mixesInheritedPropertiesIntoPropertyTable() {
        ClassMetaData classMetaData = classMetaData()
        PropertyMetaData propertyMetaData = property('propName', classMetaData, comment: 'propName comment')
        PropertyMetaData inherited2MetaData = property('inherited2', classMetaData, comment: 'inherited2 comment')
        ClassDoc superClassDoc = classDoc()

        def content = parse('''
<section>
    <section><title>Properties</title>
        <table>
            <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
            <tr><td>propName</td><td>some value</td></tr>
            <tr><td>inherited2</td><td>adds extra column</td></tr>
        </table>
    </section>
    <section><title>Methods</title><table><thead><tr></tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).buildProperties().mergeProperties()
        }

        then:
        formatTree { doc.propertiesTable } == '''<table>
    <title>Properties - Class</title>
    <thead>
        <tr>
            <td>Name</td>
            <td>Description</td>
            <td>Extra column</td>
        </tr>
    </thead>
    <tr>
        <td>
            <link linkend="inherited-id">
                <literal>inherited</literal>
            </link>
        </td>
        <td>
            <para>inherited comment</para>
        </td>
        <td/>
    </tr>
    <tr>
        <td>
            <link linkend="org.gradle.Class:inherited2">
                <literal>inherited2</literal>
            </link>
        </td>
        <td>
            <para>inherited2 comment</para>
        </td>
        <td>adds extra column</td>
    </tr>
    <tr>
        <td>
            <link linkend="org.gradle.Class:propName">
                <literal>propName</literal>
            </link>
        </td>
        <td>
            <para>propName comment</para>
        </td>
        <td>some value</td>
    </tr>
</table>'''

        _ * classMetaData.findProperty('propName') >> propertyMetaData
        _ * classMetaData.findProperty('inherited2') >> inherited2MetaData
        _ * classMetaData.superClassName >> 'org.gradle.SuperClass'
        _ * docModel.getClassDoc('org.gradle.SuperClass') >> superClassDoc
        _ * superClassDoc.classProperties >> [propertyDoc('inherited'), propertyDoc('inherited2')]
    }

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

    def mergesMethodSignatureAndDescriptionIntoMethodsTable() {
        ClassMetaData classMetaData = classMetaData()
        MethodMetaData method1 = method('methodName', classMetaData, returnType: 'ReturnType1', comment: 'method comment')
        MethodMetaData method2 = method('methodName', classMetaData, returnType: 'ReturnType2', comment: 'overloaded comment', paramTypes: ['ParamType'])

        def content = parse('''
<section>
    <section><title>Methods</title>
        <table>
            <thead><tr><td>Name</td></tr></thead>
            <tr><td>methodName</td></tr>
        </table>
    </section>
    <section><title>Properties</title><table><thead><tr>Name</tr></thead></table></section>
</section>
''')

        when:
        ClassDoc doc = withCategories {
            new ClassDoc('org.gradle.Class', content, document, classMetaData, null, docModel, javadocConverter).buildMethods().mergeMethods()
        }

        then:
        formatTree { doc.methodsTable } == '''<table>
    <title>Methods - Class</title>
    <thead>
        <tr>
            <td>Name</td>
            <td>Description</td>
        </tr>
    </thead>
    <tr>
        <td>
            <link linkend="org.gradle.Class:methodName()">
                <literal>methodName</literal>
            </link>
        </td>
        <td>
            <para>method comment</para>
        </td>
    </tr>
    <tr>
        <td>
            <link linkend="org.gradle.Class:methodName(ParamType)">
                <literal>methodName</literal>
            </link>
        </td>
        <td>
            <para>overloaded comment</para>
        </td>
    </tr>
</table>'''

        _ * classMetaData.declaredMethods >> ([method1, method2] as Set)
    }

    def classMetaData(String name = 'org.gradle.Class') {
        ClassMetaData classMetaData = Mock()
        _ * classMetaData.className >> name
        return classMetaData
    }

    def classDoc(String name = 'org.gradle.Class') {
        ClassDoc doc = Mock()
        return doc
    }

    def property(String name, ClassMetaData classMetaData) {
        return property([:], name, classMetaData)
    }

    def property(Map<String, ?> args, String name, ClassMetaData classMetaData) {
        PropertyMetaData property = Mock()
        _ * property.name >> name
        _ * property.ownerClass >> classMetaData
        _ * property.type >> new TypeMetaData(args.type ?: 'org.gradle.Type')
        _ * property.signature >> "$name-signature"
        _ * javadocConverter.parse(property) >> ({[parse("<para>${args.comment ?: 'comment'}</para>")]} as DocComment)
        return property
    }

    def propertyDoc(String name) {
        PropertyDoc propertyDoc = Mock()
        _ * propertyDoc.name >> name
        _ * propertyDoc.id >> "$name-id"
        _ * propertyDoc.description >> parse("<para>$name comment</para>")
        _ * propertyDoc.metaData >> property(name, null)
        _ * propertyDoc.additionalValues >> []
        return propertyDoc
    }

    def method(String name, ClassMetaData classMetaData) {
        return method([:], name, classMetaData)
    }

    def method(Map<String, ?> args, String name, ClassMetaData classMetaData) {
        MethodMetaData method = Mock()
        List<String> paramTypes = args.paramTypes ?: []
        _ * method.name >> name
        _ * method.overrideSignature >> "$name(${paramTypes.join(', ')})"
        _ * method.ownerClass >> classMetaData
        _ * method.returnType >> new TypeMetaData(args.returnType ?: 'ReturnType')
        _ * javadocConverter.parse(method) >> ({[parse("<para>${args.comment ?: 'comment'}</para>")]} as DocComment)
        return method
    }

    def methodDoc(String name) {
        MethodDoc methodDoc = Mock()
        _ * methodDoc.name >> name
        _ * methodDoc.metaData >> method(name, null)
        return methodDoc
    }

    def formatTree(Closure cl) {
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                return formatTree(cl.call())
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
