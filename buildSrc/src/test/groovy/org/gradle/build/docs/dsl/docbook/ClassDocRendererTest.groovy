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
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.gradle.build.docs.dsl.model.TypeMetaData
import org.gradle.build.docs.dsl.model.MethodMetaData
import org.gradle.build.docs.dsl.model.ParameterMetaData

class ClassDocRendererTest extends XmlSpecification {
    final ClassLinkRenderer linkRenderer = linkRenderer()
    final ClassDocRenderer renderer = new ClassDocRenderer(linkRenderer)

    def mergesClassMetaDataIntoMainSection() {
        def content = parse('''
            <chapter>
                <para>Some custom content</para>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('org.gradle.Class', id: 'classId', content: content, comment: 'class comment')

        when:
        withCategories {
            renderer.mergeDescription(classDoc)
        }

        then:
        formatTree(content) == '''<chapter id="classId">
    <title>Class</title>
    <segmentedlist>
        <segtitle>API Documentation</segtitle>
        <seglistitem>
            <seg>
                <apilink class="org.gradle.Class" style="java"/>
            </seg>
        </seglistitem>
    </segmentedlist>
    <para>class comment</para>
    <para>Some custom content</para>
</chapter>'''
    }
    
    def mergesPropertyMetaDataIntoPropertiesSection() {
        def content = parse('''
            <section><title>Properties</title>
                <table>
                    <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
                    <tr><td>propName</td><td>some value</td></tr>
                </table>
            </section>
        ''')

        ClassDoc classDoc = classDoc('Class', properties: content)
        PropertyDoc propDoc = propertyDoc('propName', id: 'propId', description: 'prop description', comment: 'prop comment', type: 'org.gradle.Type')
        _ * classDoc.classProperties >> [propDoc]
        _ * propDoc.additionalValues >> [parse('<td>some value</td>')]

        when:
        withCategories {
            renderer.mergeProperties(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <title>Properties</title>
    <table>
        <title>Properties - Class</title>
        <thead>
            <tr>
                <td>Property</td>
                <td>Description</td>
            </tr>
        </thead>
        <tr>
            <td>
                <link linkend="propId">
                    <literal>propName</literal>
                </link>
            </td>
            <td>
                <para>prop description</para>
            </td>
        </tr>
    </table>
    <section id="propId" role="detail">
        <title>
            <classname>org.gradle.Type</classname>
            <literal role="name">propName</literal> (read-only)
        </title>
        <para>prop comment</para>
        <segmentedlist>
            <segtitle>Extra column</segtitle>
            <seglistitem>
                <seg>some value</seg>
            </seglistitem>
        </segmentedlist>
    </section>
</section>'''
    }

    def removesPropertiesTableWhenClassHasNoProperties() {
        def content = parse('''
            <section><title>Properties</title>
                <table>
                    <thead><tr><td>Name</td></tr></thead>
                </table>
            </section>
        ''')

        ClassDoc classDoc = classDoc('Class', properties: content)
        _ * classDoc.classProperties >> []

        when:
        withCategories {
            renderer.mergeProperties(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <title>Properties</title>
    <para>No properties</para>
</section>'''
    }

    def mergesMethodMetaDataIntoMethodsSection() {
        def content = parse('''
            <section><title>Methods</title>
                <table>
                    <thead><tr><td>Name</td></tr></thead>
                    <tr><td>methodName</td></tr>
                </table>
            </section>
        ''')

        ClassDoc classDoc = classDoc('Class', methods: content)
        MethodDoc method1 = methodDoc('methodName', id: 'method1Id', returnType: 'ReturnType1', description: 'method description', comment: 'method comment')
        MethodDoc method2 = methodDoc('methodName', id: 'method2Id', returnType: 'ReturnType2', description: 'overloaded description', comment: 'overloaded comment', paramTypes: ['ParamType'])
        _ * classDoc.classMethods >> [method1, method2]


        when:
        withCategories {
            renderer.mergeMethods(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <title>Methods</title>
    <table>
        <title>Methods - Class</title>
        <thead>
            <tr>
                <td>Method</td>
                <td>Description</td>
            </tr>
        </thead>
        <tr>
            <td>
                <link linkend="method1Id">
                    <literal>methodName</literal>
                </link>
            </td>
            <td>
                <para>method description</para>
            </td>
        </tr>
        <tr>
            <td>
                <link linkend="method2Id">
                    <literal>methodName</literal>
                </link>
            </td>
            <td>
                <para>overloaded description</para>
            </td>
        </tr>
    </table>
    <section id="method1Id" role="detail">
        <title>
            <classname>ReturnType1</classname>
            <literal role="name">methodName</literal>()
        </title>
        <para>method comment</para>
    </section>
    <section id="method2Id" role="detail">
        <title>
            <classname>ReturnType2</classname>
            <literal role="name">methodName</literal>(
            <classname>ParamType</classname> p)
        </title>
        <para>overloaded comment</para>
    </section>
</section>'''
    }

    def removesMethodsTableWhenClassHasNoMethods() {
        def content = parse('''
            <section><title>Methods</title>
                <table>
                    <thead><tr><td>Name</td></tr></thead>
                </table>
            </section>
        ''')

        ClassDoc classDoc = classDoc('Class', methods: content)
        _ * classDoc.classMethods >> []

        when:
        withCategories {
            renderer.mergeMethods(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <title>Methods</title>
    <para>No methods</para>
</section>'''
    }

    def mergesBlockMetaDataIntoBlocksSection() {
        def content = parse('''
            <section>
                <section><title>Properties</title></section>
            </section>
        ''')
        ClassDoc classDoc = classDoc('Class', content: content)
        BlockDoc block = blockDoc('block', id: 'blockId', description: 'block description', comment: 'block comment')
        _ * classDoc.classBlocks >> [block]

        when:
        withCategories {
            renderer.mergeBlocks(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <section>
        <title>Properties</title>
    </section>
    <section>
        <title>Script blocks</title>
        <table>
            <title>Script blocks - Class</title>
            <thead>
                <tr>
                    <td>Block</td>
                    <td>Description</td>
                </tr>
            </thead>
            <tr>
                <td>
                    <link linkend="blockId">
                        <literal>block</literal>
                    </link>
                </td>
                <td>
                    <para>block description</para>
                </td>
            </tr>
        </table>
        <section id="blockId" role="detail">
            <title>
                <literal role="name">block</literal> { }
            </title>
            <para>block comment</para>
        </section>
    </section>
</section>'''
    }

    def doesNotAddBlocksTableWhenClassHasNoScriptBlocks() {
        def content = parse('''
            <section>
                <section><title>Properties</title></section>
            </section>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        _ * classDoc.classBlocks >> []

        when:
        withCategories {
            renderer.mergeBlocks(classDoc)
        }

        then:
        formatTree(content) == '''<section>
    <section>
        <title>Properties</title>
    </section>
    <section>
        <title>Script blocks</title>
        <para>No script blocks</para>
    </section>
</section>'''
    }

    def linkRenderer() {
        ClassLinkRenderer renderer = Mock()
        _ * renderer.link(!null) >> { args -> parse("<classname>${args[0].signature}</classname>") }
        return renderer
    }

    def classDoc(Map<String, ?> args = [:], String name) {
        ClassDoc classDoc = Mock()
        def content = args.content ?: parse('<section/>')
        def propertiesSection = withCategories { args.properties ?: content.section.find { it.title[0].text().trim() == 'Properties' } }
        def propertiesTable = withCategories { propertiesSection ? propertiesSection.table[0] : parse('<table/>')}
        def methodsSection = withCategories { args.methods ?: content.section.find { it.title[0].text().trim() == 'Methods' } }
        def methodsTable = withCategories { methodsSection ? methodsSection.table[0] : parse('<table/>') }
        _ * classDoc.simpleName >> name.split('\\.').last()
        _ * classDoc.name >> name
        _ * classDoc.id >> (args.id ?: name)
        _ * classDoc.classSection >> content
        _ * classDoc.propertiesSection >> propertiesSection
        _ * classDoc.propertiesTable >> propertiesTable
        _ * classDoc.methodsSection >> methodsSection
        _ * classDoc.methodsTable >> methodsTable
        _ * classDoc.description >> parse("<para>${args.description ?: 'description'}</para>")
        _ * classDoc.comment >> [parse("<para>${args.comment ?: 'comment'}</para>")]
        _ * classDoc.style >> 'java'
        return classDoc
    }

    def propertyDoc(Map<String, ?> args = [:], String name) {
        PropertyDoc propDoc = Mock()
        PropertyMetaData propMetaData = Mock()
        _ * propDoc.name >> name
        _ * propDoc.id >> (args.id ?: name)
        _ * propDoc.description >> parse("<para>${args.description ?: 'description'}</para>")
        _ * propDoc.comment >> [parse("<para>${args.comment ?: 'comment'}</para>")]
        _ * propDoc.metaData >> propMetaData
        _ * propMetaData.type >> new TypeMetaData(args.type ?: 'SomeType')
        return propDoc
    }

    def methodDoc(Map<String, ?> args = [:], String name) {
        MethodDoc methodDoc = Mock()
        MethodMetaData metaData = Mock()
        _ * methodDoc.name >> name
        _ * methodDoc.id >> args.id
        _ * methodDoc.description >> parse("<para>${args.description ?: 'description'}</para>")
        _ * methodDoc.comment >> [parse("<para>${args.comment ?: 'comment'}</para>")]
        _ * methodDoc.metaData >> metaData
        _ * metaData.returnType >> new TypeMetaData(args.returnType ?: 'ReturnType')
        def paramTypes = args.paramTypes ?: []
        _ * metaData.parameters >> paramTypes.collect {
            def param = new ParameterMetaData("p", metaData);
            param.type = new TypeMetaData(it)
            return param
        }
        return methodDoc
    }

    def blockDoc(Map<String, ?> args = [:], String name) {
        BlockDoc blockDoc = Mock()
        _ * blockDoc.name >> name
        _ * blockDoc.id >> (args.id ?: name)
        _ * blockDoc.description >> parse("<para>${args.description ?: 'description'}</para>")
        _ * blockDoc.comment >> [parse("<para>${args.comment ?: 'comment'}</para>")]
        blockDoc
    }
}
