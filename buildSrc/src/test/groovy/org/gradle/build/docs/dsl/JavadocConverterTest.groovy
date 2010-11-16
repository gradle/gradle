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
package org.gradle.build.docs.dsl

import spock.lang.Specification
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import groovy.xml.dom.DOMUtil

class JavadocConverterTest extends Specification {
    final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    final JavadocConverter parser = new JavadocConverter(document)

    def removesLeadingAsterixFromEachLine() {
        when:
        def result = parser.parse(''' * line 1
 * line 2
''')

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>line 1
line 2</para>
</root>
'''
    }

    def ignoresTagBlock() {
        when:
        def result = parser.parse(''' * line 1
 * @tag line 2
 * line 3
''')

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
''')

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
''')

        then:
        result.docbook == []
    }

    def firstSentenceEndsAtEndOfFirstLine() {
        when:
        def result = parser.parse(''' * first sentence
 *
 * @tag line 2
''')

        then:
        format(result.firstSentence) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>first sentence</para>
</root>
'''
    }

    def firstSentenceEndsAtEndOfFirstPeriodFollowedByWhitespace() {
        when:
        def result = parser.parse(''' * first sentence. second sentence
 * @tag line 2
''')

        then:
        format(result.firstSentence) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>first sentence.</para>
</root>
'''
    }

    def firstSentenceIsEmptyWhenNoDescription() {
        when:
        def result = parser.parse(''' *
 * @tag ignore-me
''')

        then:
        result.firstSentence == []
    }

    def convertsPTagsToParaTags() {
        when:
        def result = parser.parse('<p>para 1</p><p>para 2</p>')

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
        def result = parser.parse('This is <code>code</code>. So is {@code this}.')

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <para>This is <literal>code</literal>. So is <literal>this</literal>.</para>
</root>
'''
    }

    def doesNotInterpretContentsOfCodeTagAsHtml() {
        when:
        def result = parser.parse('{@code List<String> && a < 9}')

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
''')

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
        def result = parser.parse('<ul><li>item1</li></ul>')

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
        def result = parser.parse('{@link someClass}')

        then:
        format(result.docbook) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <apilink class="someClass"/>
</root>
'''
    }

    def convertsAnEmTag() {
        when:
        def result = parser.parse('<em>text</em>')

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
''')

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

    def format(Iterable<? extends Node> nodes) {
        document.appendChild(document.createElement('root'))
        nodes.each { node ->
            document.documentElement.appendChild(node)
        }
        return DOMUtil.serialize(document.documentElement)
    }
}
