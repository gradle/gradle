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
import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.PropertyMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData

class JavadocConverterTest extends XmlSpecification {
    final ClassMetaData classMetaData = Mock()
    final JavadocLinkConverter linkConverter = Mock()
    final GenerationListener listener = Mock()
    final JavadocConverter parser = new JavadocConverter(document, linkConverter)

    def respectsLineIndentation() {
        _ * classMetaData.rawCommentText >> '''
 * x
 *   indented
'''
        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook).contains('x\n  indented')
    }

    def removesLeadingAsterixFromEachLine() {
        _ * classMetaData.rawCommentText >> ''' * line 1
 * line 2
'''
        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>line 1
line 2</para>'''
    }

    def removesTagBlockFromComment() {
        _ * classMetaData.rawCommentText >> ''' * line 1
 * @tag line 2
 * line 3
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>line 1</para>'''
    }

    def ignoresLeadingAndTrailingEmptyLines() {
        _ * classMetaData.rawCommentText >> ''' *
 * line 1
 *
 * @tag line 2
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>line 1</para>'''
    }

    def commentIsEmptyWhenThereIsNoDescription() {
        _ * classMetaData.rawCommentText >> ''' *
 *
 * @tag line 2
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        result.docbook == []
    }

    def commentCanContainHtmlEncodedCharacters() {
        _ * classMetaData.rawCommentText >> ''' * &lt;&gt;&amp; &#47;>'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>&lt;&gt;&amp; /&gt;</para>'''
    }

    def ignoresHtmlComments() {
        _ * classMetaData.rawCommentText >> '<!-- <p>ignore me</p> --><p>para 1'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>para 1</para>'''
    }

    def convertsPElementsToParaElements() {
        _ * classMetaData.rawCommentText >> '<p>para 1</p><P>para 2</P>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>para 1</para><para>para 2</para>'''
    }

    def addsImplicitParaElement() {
        _ * classMetaData.rawCommentText >> '<em>para 1</em><P>para 2</P>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><emphasis>para 1</emphasis></para><para>para 2</para>'''
    }

    def ignoresEmptyPElements() {
        _ * classMetaData.rawCommentText >> 'para 1<p/><p></p>para 2<p></p>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>para 1</para><para>para 2</para>'''
    }

    def convertsCodeTagsAndElementsToLiteralElements() {
        _ * classMetaData.rawCommentText >> 'This is <code>code</code>. So is {@code this}.'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>This is <literal>code</literal>. So is <literal>this</literal>.</para>'''
    }

    def convertsLiteralTagsToText() {
        _ * classMetaData.rawCommentText >> '{@literal <b>markup</b> {@ignore}}'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>&lt;b&gt;markup&lt;/b&gt; {@ignore}</para>'''
    }

    def doesNotInterpretContentsOfCodeTagAsHtml() {
        _ * classMetaData.rawCommentText >> '{@code List<String> && a < 9} <code>&amp;</code>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><literal>List&lt;String&gt; &amp;&amp; a &lt; 9</literal> <literal>&amp;</literal></para>'''
    }

    def convertsPreElementsToProgramListingElements() {
        _ * classMetaData.rawCommentText >> ''' * <pre>this is some
 *
 * literal code</pre>
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<programlisting language="java">this is some

literal code</programlisting>'''
    }

    def preElementCanContainReservedCharacters() {
        _ * classMetaData.rawCommentText >> ''' * <pre>a << b</pre>'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<programlisting language="java">a &lt;&lt; b</programlisting>'''
    }

    def implicitlyEndsCurrentParagraphAtNextBlockElement() {
        _ * classMetaData.rawCommentText >> ''' * for example: <pre>this is some
 * literal code</pre> does something.
<p>another para.
<ul><li>item1</li></ul>
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>for example: </para><programlisting language="java">this is some
literal code</programlisting><para> does something.
</para><para>another para.
</para><itemizedlist><listitem>item1</listitem></itemizedlist>'''
    }

    def implicitlyEndsCurrentLiAtNextLiElement() {
        _ * classMetaData.rawCommentText >> '''<ul><li>item 1<li>item 2</ul>'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<itemizedlist><listitem>item 1</listitem><listitem>item 2</listitem></itemizedlist>'''
    }

    def convertsUlAndLiElementsToItemizedListElements() {
        _ * classMetaData.rawCommentText >> '<ul><li>item1</li></ul>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<itemizedlist><listitem>item1</listitem></itemizedlist>'''
    }

    def convertsOlAndLiElementsToOrderedListElements() {
        _ * classMetaData.rawCommentText >> '<ol><li>item1</li></ol>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<orderedlist><listitem>item1</listitem></orderedlist>'''
    }

    def convertsALinkTag() {
        _ * classMetaData.rawCommentText >> '{@link someClass} {@link otherClass#method(a, b) label}'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><xref/> <xref/></para>'''
        1 * linkConverter.resolve('someClass', classMetaData, listener) >> document.createElement("xref")
        1 * linkConverter.resolve('otherClass#method(a, b) label', classMetaData, listener) >> document.createElement("xref")
        0 * linkConverter._
    }

    def convertsAnAElementWithNameAttribute() {
        _ * classMetaData.rawCommentText >> '<a name="anchor"/>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '<anchor id="org.gradle.Class.anchor"/>'
        _ * classMetaData.className >> 'org.gradle.Class'
    }

    def convertsAnAElementWithAUrlFragment() {
        _ * classMetaData.rawCommentText >> '<a href="#anchor">some value</a>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '<para><link linkend="org.gradle.Class.anchor">some value</link></para>'
        _ * classMetaData.className >> 'org.gradle.Class'
    }

    def convertsAnAElementWithAnHref() {
        _ * classMetaData.rawCommentText >> '<a href="http://gradle.org">some value</a>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '<para><ulink url="http://gradle.org">some value</ulink></para>'
    }

    def convertsAnEmElementToAnEmphasisElement() {
        _ * classMetaData.rawCommentText >> '<em>text</em>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><emphasis>text</emphasis></para>'''
    }

    def convertsAStrongElementToAnEmphasisElement() {
        _ * classMetaData.rawCommentText >> '<strong>text</strong>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><emphasis>text</emphasis></para>'''
    }

    def convertsBAndIElementToAnEmphasisElement() {
        _ * classMetaData.rawCommentText >> '<i>text</i> <b>other</b>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><emphasis>text</emphasis> <emphasis>other</emphasis></para>'''
    }

    def convertsTTElementToALiteralElement() {
        _ * classMetaData.rawCommentText >> '<tt>text</tt>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para><literal>text</literal></para>'''
    }

    def convertsDlElementToVariableList() {
        _ * classMetaData.rawCommentText >> '<dl><dt>term<dd>definition<dt>term 2<dd>definition 2</dd></dl>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<variablelist><varlistentry><term>term</term><listitem>definition</listitem></varlistentry><varlistentry><term>term 2</term><listitem>definition 2</listitem></varlistentry></variablelist>'''
    }

    def convertsHeadingsToSections() {
        _ * classMetaData.rawCommentText >> '''
<h2>section1</h2>
text1
<h3>section 1.1</h3>
text2
<h2>section 2</h2>
text3
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<section><title>section1</title><para>
text1
</para><section><title>section 1.1</title><para>
text2
</para></section></section><section><title>section 2</title><para>
text3</para></section>'''
    }

    def convertsTable() {
        _ * classMetaData.rawCommentText >> '''
<table>
    <tr><th>column1</th><th>column2</th></tr>
    <tr><td>cell1</td><td>cell2</td></tr>
</table>
'''

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<table><thead><tr><td>column1</td><td>column2</td></tr></thead><tr><td>cell1</td><td>cell2</td></tr></table>'''
    }

    def convertsPropertyGetterMethodCommentToPropertyComment() {
        PropertyMetaData propertyMetaData = Mock()
        _ * propertyMetaData.rawCommentText >> 'returns the name of the thing.'

        when:
        def result = parser.parse(propertyMetaData, listener)

        then:
        format(result.docbook) == '''<para>The name of the thing.</para>'''
    }

    def convertsPropertySetterMethodCommentToPropertyComment() {
        PropertyMetaData propertyMetaData = Mock()
        _ * propertyMetaData.rawCommentText >> 'sets the name of the thing.'

        when:
        def result = parser.parse(propertyMetaData, listener)

        then:
        format(result.docbook) == '''<para>The name of the thing.</para>'''
    }

    def convertsInheritDocTag() {
        PropertyMetaData propertyMetaData = Mock()
        PropertyMetaData overriddenMetaData = Mock()

        when:
        def result = parser.parse(propertyMetaData, listener)

        then:
        _ * propertyMetaData.rawCommentText >> 'before {@inheritDoc} after'
        _ * propertyMetaData.overriddenProperty >> overriddenMetaData
        _ * overriddenMetaData.rawCommentText >> ''' *
 * <em>inherited value</em>
 * <p>another
 *
'''
        format(result.docbook) == '''<para>before </para><para><emphasis>inherited value</emphasis>
</para><para>another</para><para> after</para>'''
    }

    def convertsValueTag() {
        PropertyMetaData propertyMetaData = Mock()

        when:
        def result = parser.parse(propertyMetaData, listener)

        then:
        _ * propertyMetaData.rawCommentText >> '{@value org.gradle.SomeClass#CONST}'
        _ * propertyMetaData.ownerClass >> classMetaData
        _ * linkConverter.resolveValue('org.gradle.SomeClass#CONST', classMetaData, listener) >> document.importNode(parse('<literal>some-value</literal>'), true)

        format(result.docbook) == '''<para><literal>some-value</literal></para>'''
    }

    def convertsMethodComment() {
        MethodMetaData methodMetaData = Mock()
        _ * methodMetaData.rawCommentText >> 'a method.'

        when:
        def result = parser.parse(methodMetaData, listener)

        then:
        format(result.docbook) == '''<para>a method.</para>'''
    }

    def convertsUnknownElementsAndTags() {
        PropertyMetaData propertyMetaData = Mock()
        _ * propertyMetaData.rawCommentText >> '<unknown>text</unknown><inheritDoc>{@unknown text}{@p text}{@ unknown}'

        when:
        def result = parser.parse(propertyMetaData, listener)

        then:
        format(result.docbook) == '''<para><UNHANDLED-ELEMENT>&lt;unknown&gt;text&lt;/unknown&gt;</UNHANDLED-ELEMENT><UNHANDLED-ELEMENT>&lt;inheritdoc&gt;<UNHANDLED-TAG>{@unknown text}</UNHANDLED-TAG><UNHANDLED-TAG>{@p text}</UNHANDLED-TAG><UNHANDLED-TAG>{@ unknown}</UNHANDLED-TAG>&lt;/inheritdoc&gt;</UNHANDLED-ELEMENT></para>'''
    }

    def handlesMissingStartTags() {
        _ * classMetaData.rawCommentText >> 'a para</b></p>'

        when:
        def result = parser.parse(classMetaData, listener)

        then:
        format(result.docbook) == '''<para>a para</para>'''
    }
}
