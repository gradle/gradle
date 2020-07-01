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

import gradlebuild.docs.dsl.docbook.model.BlockDoc
import gradlebuild.docs.dsl.docbook.model.ClassDoc
import gradlebuild.docs.dsl.docbook.model.ClassExtensionDoc
import gradlebuild.docs.dsl.docbook.model.ExtraAttributeDoc
import gradlebuild.docs.dsl.docbook.model.MethodDoc
import gradlebuild.docs.dsl.docbook.model.PropertyDoc
import gradlebuild.docs.XmlSpecification
import gradlebuild.docs.dsl.source.model.MethodMetaData
import gradlebuild.docs.dsl.source.model.ParameterMetaData
import gradlebuild.docs.dsl.source.model.PropertyMetaData
import gradlebuild.docs.dsl.source.model.TypeMetaData

class ClassDocRendererTest extends XmlSpecification {
    final LinkRenderer linkRenderer = linkRenderer()
    final ClassDocRenderer renderer = new ClassDocRenderer(linkRenderer)

    def rendersContentForEmptyClass() {
        def sourceContent = parse('''
            <chapter>
                <section><title>Properties</title></section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('org.gradle.Class', id: 'classId', content: sourceContent, comment: 'class comment')
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<root/>')
        withCategories {
            renderer.mergeContent(classDoc, result)
        }

        then:
        formatTree(result) == '''<root>
    <chapter id="classId">
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
        <section>
            <title>Properties</title>
            <para>No properties</para>
        </section>
        <section>
            <title>Methods</title>
            <para>No methods</para>
        </section>
        <section>
            <title>Script blocks</title>
            <para>No script blocks</para>
        </section>
    </chapter>
</root>'''
    }

    def rendersKnownSubtypes() {
        def sourceContent = parse('''
            <chapter>
                <section><title>Properties</title></section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('org.gradle.Class', id: 'classId', content: sourceContent, comment: 'class comment')
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        def subtypes = [ "org.gradle.Subtype1", "org.gradle.Subtype2", "org.gradle.Subtype3", "org.gradle.Subtype4" ].collect {
            ClassDoc subtype = Mock()
            _ * subtype.name >> it
            subtype
        }
        _ * classDoc.subClasses >> subtypes

        when:
        def result = parse('<root/>')
        withCategories {
            renderer.mergeContent(classDoc, result)
        }

        then:
        formatTree(result) == '''<root>
    <chapter id="classId">
        <title>Class</title>
        <segmentedlist>
            <segtitle>API Documentation</segtitle>
            <seglistitem>
                <seg>
                    <apilink class="org.gradle.Class" style="java"/>
                </seg>
            </seglistitem>
        </segmentedlist>
        <segmentedlist>
            <segtitle>Known Subtypes</segtitle>
            <seglistitem>
                <seg>
                    <simplelist columns="3" type="vert">
                        <member>
                            <apilink class="org.gradle.Subtype1"/>
                        </member>
                        <member>
                            <apilink class="org.gradle.Subtype2"/>
                        </member>
                        <member>
                            <apilink class="org.gradle.Subtype3"/>
                        </member>
                        <member>
                            <apilink class="org.gradle.Subtype4"/>
                        </member>
                    </simplelist>
                </seg>
            </seglistitem>
        </segmentedlist>
        <para>class comment</para>
        <section>
            <title>Properties</title>
            <para>No properties</para>
        </section>
        <section>
            <title>Methods</title>
            <para>No methods</para>
        </section>
        <section>
            <title>Script blocks</title>
            <para>No script blocks</para>
        </section>
    </chapter>
</root>'''
    }

    def mergesClassMetaDataIntoMainSection() {
        def sourceContent = parse('''
            <chapter>
                <para>Some custom content</para>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('org.gradle.Class', id: 'classId', content: sourceContent, comment: 'class comment')
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<root/>')
        withCategories {
            renderer.mergeContent(classDoc, result)
        }

        then:
        formatTree(result) == '''<root>
    <chapter id="classId">
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
        <section>
            <title>Properties</title>
            <para>No properties</para>
        </section>
        <section>
            <title>Methods</title>
            <para>No methods</para>
        </section>
        <section>
            <title>Script blocks</title>
            <para>No script blocks</para>
        </section>
    </chapter>
</root>'''
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

        def extraAttribute = new ExtraAttributeDoc(parse('<td>Extra column</td>'), parse('<td>some value</td>'))
        ClassDoc classDoc = classDoc('Class', content: content)
        PropertyDoc propDoc = propertyDoc('propName', id: 'propId', description: 'prop description', comment: 'prop comment', type: 'org.gradle.Type', attributes: [extraAttribute])
        _ * classDoc.classProperties >> [propDoc]
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
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
        <title>Methods</title>
        <para>No methods</para>
    </section>
    <section>
        <title>Script blocks</title>
        <para>No script blocks</para>
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

    def rendersDeprecatedAndIncubatingProperties() {
        def content = parse('''
            <chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                        <tr><td>deprecatedProperty</td></tr>
                        <tr><td>incubatingProperty</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        PropertyDoc deprecatedProp = propertyDoc('deprecatedProperty', id: 'prop1', description: 'prop1 description', comment: 'prop1 comment', type: 'org.gradle.Type', deprecated: true)
        PropertyDoc incubatingProp = propertyDoc('incubatingProperty', id: 'prop2', description: 'prop2 description', comment: 'prop2 comment', type: 'org.gradle.Type', incubating: true)
        _ * classDoc.classProperties >> [deprecatedProp, incubatingProp]
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
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
                    <link linkend="prop1">
                        <literal>deprecatedProperty</literal>
                    </link>
                </td>
                <td>
                    <caution>Deprecated</caution>
                    <para>prop1 description</para>
                </td>
            </tr>
            <tr>
                <td>
                    <link linkend="prop2">
                        <literal>incubatingProperty</literal>
                    </link>
                </td>
                <td>
                    <caution>Incubating</caution>
                    <para>prop2 description</para>
                </td>
            </tr>
        </table>
    </section>
    <section>
        <title>Methods</title>
        <para>No methods</para>
    </section>
    <section>
        <title>Script blocks</title>
        <para>No script blocks</para>
    </section>
    <section>
        <title>Property details</title>
        <section id="prop1" role="detail">
            <title><classname>org.gradle.Type</classname> <literal>deprecatedProperty</literal> (read-only)</title>
            <caution>
                <para>Note: This property is <ulink url="../userguide/feature_lifecycle.html">deprecated</ulink> and will be removed in the next major version of Gradle.</para>
            </caution>
            <para>prop1 comment</para>
        </section>
        <section id="prop2" role="detail">
            <title><classname>org.gradle.Type</classname> <literal>incubatingProperty</literal> (read-only)</title>
            <caution>
                <para>Note: This property is <ulink url="../userguide/feature_lifecycle.html">incubating</ulink> and may change in a future version of Gradle.</para>
            </caution>
            <para>prop2 comment</para>
        </section>
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
        _ * targetClassDoc.classProperties >> []
        _ * targetClassDoc.classMethods >> []
        _ * targetClassDoc.classBlocks >> []
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * targetClassDoc.subClasses >> []
        _ * extensionDoc.extensionProperties >> [propertyDoc]
        _ * extensionDoc.extensionMethods >> []
        _ * extensionDoc.extensionBlocks >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(targetClassDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
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
        <title>Methods</title>
        <para>No methods</para>
    </section>
    <section>
        <title>Script blocks</title>
        <para>No script blocks</para>
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
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> [method1, method2]
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
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
        <title>Script blocks</title>
        <para>No script blocks</para>
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

    def rendersDeprecatedAndIncubatingMethods() {
        def content = parse('''
            <chapter>
                <section><title>Methods</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                        <tr><td>deprecated</td></tr>
                        <tr><td>incubating</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        MethodDoc method1 = methodDoc('deprecated', id: 'method1Id', returnType: 'ReturnType1', description: 'method description', comment: 'method comment', deprecated: true)
        MethodDoc method2 = methodDoc('incubating', id: 'method2Id', returnType: 'ReturnType2', description: 'overloaded description', comment: 'overloaded comment', paramTypes: ['ParamType'], incubating: true)
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> [method1, method2]
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
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
                    <literal><link linkend="method1Id">deprecated</link>()</literal>
                </td>
                <td>
                    <caution>Deprecated</caution>
                    <para>method description</para>
                </td>
            </tr>
            <tr>
                <td>
                    <literal><link linkend="method2Id">incubating</link>(p)</literal>
                </td>
                <td>
                    <caution>Incubating</caution>
                    <para>overloaded description</para>
                </td>
            </tr>
        </table>
    </section>
    <section>
        <title>Script blocks</title>
        <para>No script blocks</para>
    </section>
    <section>
        <title>Method details</title>
        <section id="method1Id" role="detail">
            <title><classname>ReturnType1</classname> <literal>deprecated</literal>()</title>
            <caution>
                <para>Note: This method is <ulink url="../userguide/feature_lifecycle.html">deprecated</ulink> and will be removed in the next major version of Gradle.</para>
            </caution>
            <para>method comment</para>
        </section>
        <section id="method2Id" role="detail">
            <title><classname>ReturnType2</classname> <literal>incubating</literal>(<classname>ParamType</classname> p)</title>
            <caution>
                <para>Note: This method is <ulink url="../userguide/feature_lifecycle.html">incubating</ulink> and may change in a future version of Gradle.</para>
            </caution>
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
        _ * targetClassDoc.classProperties >> []
        _ * targetClassDoc.classMethods >> []
        _ * targetClassDoc.classBlocks >> []
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * targetClassDoc.subClasses >> []
        _ * extensionDoc.extensionProperties >> []
        _ * extensionDoc.extensionMethods >> [methodDoc]
        _ * extensionDoc.extensionBlocks >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(targetClassDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
    <section>
        <title>Methods</title>
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
        <title>Script blocks</title>
        <para>No script blocks</para>
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


    def mergesSummariesBeforeDetails() {
        def content = parse('''
            <chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td><td>Extra column</td></tr></thead>
                        <tr><td>propName</td><td>some value</td></tr>
                    </table>
                </section>
                <section><title>Methods</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                        <tr><td>methodName</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        PropertyDoc property1 = propertyDoc('propName', id: 'propId', type: 'org.gradle.Type', description: 'prop description', comment: 'prop comment')
        MethodDoc method1 = methodDoc('methodName', id: 'methodId', returnType: 'ReturnType', description: 'method description', comment: 'method comment')
        BlockDoc block1 = blockDoc('blockName', id: 'blockId', type: 'org.gradle.Type', description: 'block description', comment: 'block comment')
        _ * classDoc.classProperties >> [property1]
        _ * classDoc.classMethods >> [method1]
        _ * classDoc.classBlocks >> [block1]
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
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
                    <literal><link linkend="methodId">methodName</link>()</literal>
                </td>
                <td>
                    <para>method description</para>
                </td>
            </tr>
        </table>
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
                        <literal>blockName</literal>
                    </link>
                </td>
                <td>
                    <para>block description</para>
                </td>
            </tr>
        </table>
    </section>
    <section>
        <title>Property details</title>
        <section id="propId" role="detail">
            <title><classname>org.gradle.Type</classname> <literal>propName</literal> (read-only)</title>
            <para>prop comment</para>
        </section>
    </section>
    <section>
        <title>Method details</title>
        <section id="methodId" role="detail">
            <title><classname>ReturnType</classname> <literal>methodName</literal>()</title>
            <para>method comment</para>
        </section>
    </section>
    <section>
        <title>Script block details</title>
        <section id="blockId" role="detail">
            <title><literal>blockName</literal> { }</title>
            <para>block comment</para>
            <segmentedlist>
                <segtitle>Delegates to</segtitle>
                <seglistitem>
                    <seg><classname>org.gradle.Type</classname> from <link linkend="blockName">
                            <literal>blockName</literal></link></seg>
                </seglistitem>
            </segmentedlist>
        </section>
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
        BlockDoc block1 = blockDoc('block1', id: 'block1', description: 'block1 description', comment: 'block1 comment', type: 'org.gradle.Type')
        BlockDoc block2 = blockDoc('block2', id: 'block2', description: 'block2 description', comment: 'block2 comment', type: 'org.gradle.Type', multivalued: true)
        _ * classDoc.classProperties >> []
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> [block1, block2]
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
    <section>
        <title>Methods</title>
        <para>No methods</para>
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
                    <link linkend="block1">
                        <literal>block1</literal>
                    </link>
                </td>
                <td>
                    <para>block1 description</para>
                </td>
            </tr>
            <tr>
                <td>
                    <link linkend="block2">
                        <literal>block2</literal>
                    </link>
                </td>
                <td>
                    <para>block2 description</para>
                </td>
            </tr>
        </table>
    </section>
    <section>
        <title>Script block details</title>
        <section id="block1" role="detail">
            <title><literal>block1</literal> { }</title>
            <para>block1 comment</para>
            <segmentedlist>
                <segtitle>Delegates to</segtitle>
                <seglistitem>
                    <seg><classname>org.gradle.Type</classname> from <link linkend="block1">
                            <literal>block1</literal></link></seg>
                </seglistitem>
            </segmentedlist>
        </section>
        <section id="block2" role="detail">
            <title><literal>block2</literal> { }</title>
            <para>block2 comment</para>
            <segmentedlist>
                <segtitle>Delegates to</segtitle>
                <seglistitem>
                    <seg>Each <classname>org.gradle.Type</classname> in <link linkend="block2">
                            <literal>block2</literal></link></seg>
                </seglistitem>
            </segmentedlist>
        </section>
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
        _ * targetClassDoc.classProperties >> []
        _ * targetClassDoc.classMethods >> []
        _ * targetClassDoc.classBlocks >> []
        _ * targetClassDoc.classExtensions >> [extensionDoc]
        _ * targetClassDoc.subClasses >> []
        _ * extensionDoc.extensionProperties >> []
        _ * extensionDoc.extensionMethods >> []
        _ * extensionDoc.extensionBlocks >> [blockDoc]

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(targetClassDoc, result)
        }

        then:
        formatTree(result) == '''<chapter>
    <section>
        <title>Properties</title>
        <para>No properties</para>
    </section>
    <section>
        <title>Methods</title>
        <para>No methods</para>
    </section>
    <section>
        <title>Script blocks</title>
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

    def rendersReadAndWriteOnlyProperties() {
        def content = parse('''
            <chapter>
                <section><title>Properties</title>
                    <table>
                        <thead><tr><td>Name</td></tr></thead>
                        <tr><td>readWriteProperty</td></tr>
                        <tr><td>readOnlyProperty</td></tr>
                        <tr><td>writeOnlyProperty</td></tr>
                    </table>
                </section>
            </chapter>
        ''')

        ClassDoc classDoc = classDoc('Class', content: content)
        PropertyDoc readWriteProperty = propertyDoc('readWriteProperty', readable: true, writeable: true)
        PropertyDoc readOnlyProperty = propertyDoc('readOnlyProperty', readable: true, writeable: false)
        PropertyDoc writeOnlyProperty = propertyDoc('writeOnlyProperty', readable: false, writeable: true)
        _ * classDoc.classProperties >> [readWriteProperty, readOnlyProperty, writeOnlyProperty]
        _ * classDoc.classMethods >> []
        _ * classDoc.classBlocks >> []
        _ * classDoc.classExtensions >> []
        _ * classDoc.subClasses >> []

        when:
        def result = parse('<chapter/>', document)
        withCategories {
            renderer.merge(classDoc, result)
        }

        then:
        formatTree(result).contains('''
    <section>
        <title>Property details</title>
        <section id="readWriteProperty" role="detail">
            <title>
                <classname>SomeType</classname>
                <literal>readWriteProperty</literal>
            </title>
            <para>comment</para>
        </section>
        <section id="readOnlyProperty" role="detail">
            <title><classname>SomeType</classname> <literal>readOnlyProperty</literal> (read-only)</title>
            <para>comment</para>
        </section>
        <section id="writeOnlyProperty" role="detail">
            <title><classname>SomeType</classname> <literal>writeOnlyProperty</literal> (write-only)</title>
            <para>comment</para>
        </section>
    </section>''')
    }

    def linkRenderer() {
        LinkRenderer renderer = Mock()
        _ * renderer.link(!null, !null) >> {
            args -> parse("<classname>${args[0].signature}</classname>", document)
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
        _ * propDoc.deprecated >> (args.deprecated ?: false)
        _ * propDoc.incubating >> (args.incubating ?: false)
        _ * propDoc.additionalValues >> (args.attributes ?: [])
        _ * propMetaData.type >> new TypeMetaData(args.type ?: 'SomeType')
        _ * propMetaData.readable >> (args.containsKey('readable') ? args.readable : true)
        _ * propMetaData.writeable >> (args.containsKey('writeable') ? args.writeable : false)
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
        _ * methodDoc.deprecated >> (args.deprecated ?: false)
        _ * methodDoc.incubating >> (args.incubating ?: false)
        _ * metaData.returnType >> new TypeMetaData(args.returnType ?: 'ReturnType')
        def paramTypes = args.paramTypes ?: []
        _ * metaData.parameters >> paramTypes.collect {
            def param = new ParameterMetaData("p");
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
        _ * blockDoc.multiValued >> (args.multivalued ?: false)
        blockDoc
    }

    def extensionDoc(Map<String, ?> args = [:], String name) {
        ClassExtensionDoc doc = Mock()
        doc.pluginId >> name
        doc
    }
}
