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
package org.gradle.build.docs

import org.w3c.dom.Element

class SampleLayoutHandlerTest extends XmlSpecification {
    final Element samples = document.createElement('samples')
    final SampleLayoutHandler builder = new SampleLayoutHandler('samples/name')
    final Element parent = document.createElement("root")

    def buildsLayoutForFileNames() {
        when:
        builder.handle('''
build.gradle
settings.gradle
''', parent)

        then:
        expectLayout('''name/
  build.gradle
  settings.gradle
''')
    }

    def buildsLayoutForDirNames() {
        when:
        builder.handle('''
src/
build/
''', parent)

        then:
        expectLayout('''name/
  src/
  build/
''')
    }

    def buildsLayoutForNestedNames() {
        when:
        builder.handle('''
api/build/
api/build.gradle
''', parent)

        then:
        expectLayout('''name/
  api/
    build/
    build.gradle
''')
    }

    def addsMetaDataToSamplesManifest() {
        when:
        builder.handleSample('''
build.gradle
build/libs/
''', samples)

        then:
        formatTree(samples) == '''<samples>
    <file path="build.gradle"/>
    <dir path="build/libs/"/>
</samples>'''
    }

    def buildsLayoutForMultipleNestingLevels() {
        when:
        builder.handle('''
src/main/java/Source1.java
src/main/java/org/
src/test/java/SourceTest.java
build/libs/test.jar
''', parent)

        then:
        expectLayout('''name/
  src/
    main/java/
      Source1.java
      org/
    test/java/
      SourceTest.java
  build/libs/
    test.jar
''')
    }

    def buildsLayoutForMultipleNestingLevelsWithExplicitDir() {
        when:
        builder.handle('''
src/main/java/
src/main/java/org/gradle/Source1.java
src/main/java/org/gradle/Source2.java
src/test/java/
src/test/java/org/gradle/SourceTest.java
''', parent)

        then:
        expectLayout('''name/
  src/
    main/java/
      org/gradle/
        Source1.java
        Source2.java
    test/java/
      org/gradle/
        SourceTest.java
''')
    }

    def failsWhenFileHasChildren() {
        when:
        builder.handle('''
src/main/java
src/main/java/org/gradle/Source1.java
''', parent)

        then:
        RuntimeException e = thrown()
        e.message == 'Parent directory \'java\' should end with a slash.'
    }

    def removesLeadingSlashAndEmptyElements() {
        when:
        builder.handle('''
/src//java//
src//java/Source.java
''', parent)

        then:
        expectLayout('''name/
  src/java/
    Source.java
''')
    }

    void expectLayout(String layout) {
        assert format(parent.childNodes.collect {it}) == """<para>Build layout</para><programlisting>$layout</programlisting>"""
    }
}
