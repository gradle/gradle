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
            <chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
                        <tr><td>propName</td><td>some value</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        PropertyDoc propDoc = propertyDoc('propName', id: 'propId', description: 'prop description', comment: 'prop comment', type: 'org.gradle.Type')
        _ * classDoc.classProperties >> [propDoc]
        _ * propDoc.additionalValues >> [new ExtraAttributeDoc(parse('<td>Extra column</td>'), parse('<td>some value</td>'))]

        when:
        withCategories {
            renderer.mergeProperties(classDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
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
    </section>
    <section>
        <title>Property details</title>
        <section id="propId" role="detail">
            <title><classname>org.gradle.Type</classname> <literal>propName</literal> (read-only)</title>
            <para>prop comment</para>
            <segmentedlist>
                <segtitle>Extra column</segtitle>
                <seglistitem>
                    <seg>some value</seg>
                </seglistitem>
            </segmentedlist>
        </section>
    </section>
</chapter>'''
    }

    def removesPropertiesTableWhenClassHasNoProperties() {
        def content = parse('''
            <chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        _ * classDoc.classProperties >> []

        when:
        withCategories {
            renderer.mergeProperties(classDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
</chapter>'''
    }

    def mergesExtensionPropertyMetaDataIntoPropertiesSection() {
        def content = parse('''<chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                    </table>
                </section>
                <section><title>Property details</title></section>
            </chapter>
        ''')

        ClassDoc targetClassDoc = classDoc('Class', content: content)
        ClassExtensionDoc extensionDoc = extensionDoc('thingo')
        PropertyDoc propertyDoc = propertyDoc('propName', id: 'propId')
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * extensionDoc.extensionProperties >> [propertyDoc]

        when:
        withCategories {
            renderer.mergeExtensionProperties(targetClassDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
        <title>Properties</title>
        <table>
            <thead>
                <tr>
                    <td>Name</td>
                </tr>
            </thead>
        </table>
        <section>
            <title>Properties added by the <literal>thingo</literal> plugin</title>
            <titleabbrev><literal>thingo</literal> plugin</titleabbrev>
            <table>
                <title>Properties - <literal>thingo</literal> plugin</title>
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
                        <para>description</para>
                    </td>
                </tr>
            </table>
        </section>
    </section>
    <section>
        <title>Property details</title>
        <section id="propId" role="detail">
            <title><classname>SomeType</classname> <literal>propName</literal> (read-only)</title>
            <para>comment</para>
        </section>
    </section>
</chapter>'''
    }

    def mergesMethodMetaDataIntoMethodsSection() {
        def content = parse('''
            <chapter>
                <section><title>Methods</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                        <tr><td>methodName</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        MethodDoc method1 = methodDoc('methodName', id: 'method1Id', returnType: 'ReturnType1', description: 'method description', comment: 'method comment')
        MethodDoc method2 = methodDoc('methodName', id: 'method2Id', returnType: 'ReturnType2', description: 'overloaded description', comment: 'overloaded comment', paramTypes: ['ParamType'])
        _ * classDoc.classMethods >> [method1, method2]


        when:
        withCategories {
            renderer.mergeMethods(classDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
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
                    <literal><link linkend="method1Id">methodName</link>()</literal>
                </td>
                <td>
                    <para>method description</para>
                </td>
            </tr>
            <tr>
                <td>
                    <literal><link linkend="method2Id">methodName</link>(p)</literal>
                </td>
                <td>
                    <para>overloaded description</para>
                </td>
            </tr>
        </table>
    </section>
    <section>
        <title>Method details</title>
        <section id="method1Id" role="detail">
            <title><classname>ReturnType1</classname> <literal>methodName</literal>()</title>
            <para>method comment</para>
        </section>
        <section id="method2Id" role="detail">
            <title><classname>ReturnType2</classname> <literal>methodName</literal>(<classname>ParamType</classname> p)</title>
            <para>overloaded comment</para>
        </section>
    </section>
</chapter>'''
    }

    def mergesExtensionMethodMetaDataIntoMethodsSection() {
        def content = parse('''
            <chapter>
                <section><title>Methods</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                    </table>
                </section>
                <section><title>Method details</title></section>
            </chapter>
        ''')

        ClassDoc targetClassDoc = classDoc('Class', content: content)
        ClassExtensionDoc extensionDoc = extensionDoc('thingo')
        MethodDoc methodDoc = methodDoc('methodName', id: 'methodId')
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * extensionDoc.extensionMethods >> [methodDoc]

        when:
        withCategories {
            renderer.mergeExtensionMethods(targetClassDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
        <title>Methods</title>
        <table>
            <thead>
                <tr>
                    <td>Name</td>
                </tr>
            </thead>
        </table>
        <section>
            <title>Methods added by the <literal>thingo</literal> plugin</title>
            <titleabbrev><literal>thingo</literal> plugin</titleabbrev>
            <table>
                <title>Methods - <literal>thingo</literal> plugin</title>
                <thead>
                    <tr>
                        <td>Method</td>
                        <td>Description</td>
                    </tr>
                </thead>
                <tr>
                    <td>
                        <literal><link linkend="methodId">methodName</link>()</literal>
                    </td>
                    <td>
                        <para>description</para>
                    </td>
                </tr>
            </table>
        </section>
    </section>
    <section>
        <title>Method details</title>
        <section id="methodId" role="detail">
            <title><classname>ReturnType</classname> <literal>methodName</literal>()</title>
            <para>comment</para>
        </section>
    </section>
</chapter>'''
    }

    def removesMethodsTableWhenClassHasNoMethods() {
        def content = parse('''
            <chapter>
                <section><title>Methods</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        _ * classDoc.classMethods >> []

        when:
        withCategories {
            renderer.mergeMethods(classDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
        <title>Methods</title>
        <para>No methods</para>
    </section>
</chapter>'''
    }

    def mergesBlockMetaDataIntoBlocksSection() {
        def content = parse('''
            <chapter>
                <section>
                    <title>Methods</title>
                </section>
            </chapter>
        ''')
        ClassDoc classDoc = classDoc('Class', content: content)
        BlockDoc block = blockDoc('block', id: 'blockId', description: 'block description', comment: 'block comment', type: 'org.gradle.Type')
        _ * classDoc.classBlocks >> [block]

        when:
        withCategories {
            renderer.mergeBlocks(classDoc)
        }

        then:
        formatTree(content) == '''<chapter>
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
    </section>
    <section>
        <title>Script block details</title>
        <section id="blockId" role="detail">
            <title><literal>block</literal> { }</title>
            <para>block comment</para>
            <segmentedlist>
                <segtitle>Delegates to</segtitle>
                <seglistitem>
                    <seg><classname>org.gradle.Type</classname> from <link linkend="block">
                            <literal>block</literal></link></seg>
                </seglistitem>
            </segmentedlist>
        </section>
    </section>
    <section>
        <title>Methods</title>
    </section>
</chapter>'''
    }

    def mergesExtensionBlockMetaDataIntoBlocksSection() {
        def content = parse('''
            <chapter>
                <section><title>Script blocks</title>
                    <table><thead/></table>
                </section>
                <section><title>Script block details</title></section>
            </chapter>
        ''')

        ClassDoc targetClassDoc = classDoc('Class', content: content)
        ClassExtensionDoc extensionDoc = extensionDoc('thingo')
        BlockDoc blockDoc = blockDoc('blockName', id: 'blockId')
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * extensionDoc.extensionBlocks >> [blockDoc]

        when:
        withCategories {
            renderer.mergeExtensionBlocks(targetClassDoc)
        }

        then:
        formatTree(content) == '''<chapter>
    <section>
        <title>Script blocks</title>
        <table>
            <thead/>
        </table>
        <section>
            <title>Script blocks added by the <literal>thingo</literal> plugin</title>
            <titleabbrev><literal>thingo</literal> plugin</titleabbrev>
            <table>
                <title>Script blocks - <literal>thingo</literal> plugin</title>
                <thead>
                    <tr>
                        <td>Block</td>
                        <td>Description</td>
                    </tr>
                </thead>
                <tr>
                    <td>
                        <link linkend="blockId">
                            <literal>blockName</literal>
                        </link>
                    </td>
                    <td>
                        <para>description</para>
                    </td>
                </tr>
            </table>
        </section>
    </section>
    <section>
        <title>Script block details</title>
        <section id="blockId" role="detail">
            <title><literal>blockName</literal> { }</title>
            <para>comment</para>
            <segmentedlist>
                <segtitle>Delegates to</segtitle>
                <seglistitem>
                    <seg><classname>BlockType</classname> from <link linkend="blockName">
                            <literal>blockName</literal></link></seg>
                </seglistitem>
            </segmentedlist>
        </section>
    </section>
</chapter>'''
    }

    def doesNotAddBlocksTableWhenClassHasNoScriptBlocks() {
        def content = parse('''
            <section>
                <section><title>Methods</title></section>
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
        <title>Script blocks</title>
        <para>No script blocks</para>
    </section>
    <section>
        <title>Methods</title>
    </section>
</section>'''
    }

    def linkRenderer() {
        ClassLinkRenderer renderer = Mock()
        _ * renderer.link(!null) >> {
            args -> parse("<classname>${args[0].signature}</classname>")
        }
        return renderer
    }

    def classDoc(Map<String, ?> args = [:], String name) {
        ClassDoc classDoc = Mock()
        def content = args.content ?: parse('<section></section>')
        def propertiesSection = withCategories { content.section.find { it.title[0].text().trim() == 'Properties' } }
        def propertyDetailsSection = withCategories { content.section.find { it.title[0].text().trim() == 'Property details' } }
        def propertiesTable = withCategories { propertiesSection ? propertiesSection.table[0] : parse('<table/>')}
        def methodsSection = withCategories { content.section.find { it.title[0].text().trim() == 'Methods' } }
        def methodDetailsSection = withCategories { content.section.find { it.title[0].text().trim() == 'Method details' } }
        def methodsTable = withCategories { methodsSection ? methodsSection.table[0] : parse('<table/>') }
        def blocksSection = withCategories { content.section.find { it.title[0].text().trim() == 'Script blocks' } }
        def blockDetailsSection = withCategories { content.section.find { it.title[0].text().trim() == 'Script block details' } }
        def blocksTable = withCategories { blocksSection ? blocksSection.table[0] : parse('<table/>') }
        _ * classDoc.simpleName >> name.split('\\.').last()
        _ * classDoc.name >> name
        _ * classDoc.id >> (args.id ?: name)
        _ * classDoc.classSection >> content
        _ * classDoc.propertiesSection >> propertiesSection
        _ * classDoc.propertiesTable >> propertiesTable
        _ * classDoc.propertyDetailsSection >> propertyDetailsSection
        _ * classDoc.methodsSection >> methodsSection
        _ * classDoc.methodsTable >> methodsTable
        _ * classDoc.methodDetailsSection >> methodDetailsSection
        _ * classDoc.blocksTable >> blocksTable
        _ * classDoc.blockDetailsSection >> blockDetailsSection
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
        PropertyDoc blockPropDoc = propertyDoc(name)
        _ * blockDoc.name >> name
        _ * blockDoc.id >> (args.id ?: name)
        _ * blockDoc.description >> parse("<para>${args.description ?: 'description'}</para>")
        _ * blockDoc.comment >> [parse("<para>${args.comment ?: 'comment'}</para>")]
        _ * blockDoc.type >> new TypeMetaData(args.type ?: 'BlockType')
        _ * blockDoc.blockProperty >> blockPropDoc
        blockDoc
    }

    def extensionDoc(Map<String, ?> args = [:], String name) {
        ClassExtensionDoc doc = Mock()
        doc.pluginId >> name
        doc
    }
}
