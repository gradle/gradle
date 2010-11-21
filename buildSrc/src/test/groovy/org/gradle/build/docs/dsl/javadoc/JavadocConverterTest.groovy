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
package org.gradle.build.docs.dsl.javadoc

import org.gradle.build.docs.dsl.XmlSpecification
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData

class JavadocConverterTest extends XmlSpecification {
    final ClassMetaData classMetaData = Mock()
    final JavadocLinkConverter linkConverter = Mock()
    final JavadocConverter parser = new JavadocConverter(document, linkConverter)

    def removesLeadingAsterixFromEachLine() {
        when:
        def result = parser.parse(''' * line 1
 * line 2
''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>line 1
line 2</para>
</root>
'''
    }

    def removesTagBlockFromComment() {
        when:
        def result = parser.parse(''' * line 1
 * @tag line 2
 * line 3
''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>line 1</para>
</root>
'''
    }

    def ignoresLeadingAndTrailingEmptyLines() {
        when:
        def result = parser.parse(''' *
 * line 1
 *
 * @tag line 2
''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>line 1</para>
</root>
'''
    }

    def commentIsEmptyWhenThereIsNoDescription() {
        when:
        def result = parser.parse(''' *
 *
 * @tag line 2
''', classMetaData)

        then:
        result.docbook == []
    }

    def commentCanContainHtmlEncodedCharacters() {
        when:
        def result = parser.parse(''' * &lt;&gt;&amp; >''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>&lt;&gt;&amp; &gt;</para>
</root>
'''
    }

    def convertsPTagsToParaTags() {
        when:
        def result = parser.parse('<p>para 1</p><P>para 2</P>', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>para 1</para>
  <para>para 2</para>
</root>
'''
    }

    def convertsCodeTagsToLiteralTags() {
        when:
        def result = parser.parse('This is <code>code</code>. So is {@code this}.', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>This is <literal>code</literal>. So is <literal>this</literal>.</para>
</root>
'''
    }

    def doesNotInterpretContentsOfCodeTagAsHtml() {
        when:
        def result = parser.parse('{@code List<String> && a < 9}', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <literal>List&lt;String&gt; &amp;&amp; a &lt; 9</literal>
</root>
'''
    }

    def convertsPreTagsToProgramListingTags() {
        when:
        def result = parser.parse('''<pre>this is some
literal code</pre>
''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <programlisting>this is some
literal code</programlisting>
</root>
'''
    }

    def convertsUlAndLiTagsToItemizedListTags() {
        when:
        def result = parser.parse('<ul><li>item1</li></ul>', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <itemizedlist>
    <listitem>item1</listitem>
  </itemizedlist>
</root>
'''
    }

    def convertsALinkTag() {
        when:
        def result = parser.parse('{@link someClass}', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <someLink/>
</root>
'''
        _ * linkConverter.resolve('someClass', classMetaData) >> [document.createElement("someLink")]
    }

    def convertsAnEmTag() {
        when:
        def result = parser.parse('<em>text</em>', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <emphasis>text</emphasis>
</root>
'''
    }

    def convertsHeadingsToSections() {
        when:
        def result = parser.parse('''
<h2>section1</h2>
text1
<h3>section 1.1</h3>
text2
<h2>section 2</h2>
text3
''', classMetaData)

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <section>
    <title>section1</title>
text1
<section>
      <title>section 1.1</title>
text2
</section>
  </section>
  <section>
    <title>section 2</title>
text3</section>
</root>
'''
    }

    def convertsInheritDocTag() {
        PropertyMetaData propertyMetaData = Mock()

        when:
        def result = parser.parse('before {@inheritDoc} after', propertyMetaData, classMetaData)

        then:
        _ * propertyMetaData.inheritedRawCommentText >> ''' *
 * <em>inherited value</em>
 *
'''
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>before <emphasis>inherited value</emphasis> after</para>
</root>
'''
    }
}
