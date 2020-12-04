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
import gradlebuild.docs.dsl.source.model.TypeMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData
import gradlebuild.docs.dsl.source.model.ClassMetaData

class LinkRendererTest extends XmlSpecification {
    final DslDocModel model = Mock()
    final GenerationListener listener = Mock()
    final LinkRenderer renderer = new LinkRenderer(document, model, "groovyVersion", "javaVersion")

    def rendersLinkToApiClass() {
        when:
        def link = renderer.link(type('org.gradle.SomeClass'), listener)

        then:
        format(link) == '<apilink class="org.gradle.SomeClass"/>'
        _ * model.isKnownType('org.gradle.SomeClass') >> true
    }

    def rendersLinkToApiClassArray() {
        when:
        def link = renderer.link(type('org.gradle.SomeClass', true), listener)

        then:
        format(link) == '<classname><apilink class="org.gradle.SomeClass"/>[]</classname>'
        _ * model.isKnownType('org.gradle.SomeClass') >> true
    }

    def rendersLinkToJavaClass() {
        when:
        def link = renderer.link(type('java.util.List'), listener)

        then:
        format(link) == '<ulink url="https://docs.oracle.com/javase/javaVersion/docs/api/java/util/List.html"><classname>List</classname></ulink>'
    }

    def rendersLinkToJavaClassArray() {
        when:
        def link = renderer.link(type('java.util.List', true), listener)

        then:
        format(link) == '<classname><ulink url="https://docs.oracle.com/javase/javaVersion/docs/api/java/util/List.html"><classname>List</classname></ulink>[]</classname>'
    }

    def rendersLinkToPrimitiveType() {
        when:
        def link = renderer.link(type('boolean'), listener)

        then:
        format(link) == '<classname>boolean</classname>'
    }

    def rendersLinkToGroovyClass() {
        when:
        def link = renderer.link(type('groovy.lang.Closure'), listener)

        then:
        format(link) == '<ulink url="https://docs.groovy-lang.org/groovyVersion/html/gapi/groovy/lang/Closure.html"><classname>Closure</classname></ulink>'
    }

    def rendersLinkToGroovyClassArray() {
        when:
        def link = renderer.link(type('groovy.lang.Closure', true), listener)

        then:
        format(link) == '<classname><ulink url="https://docs.groovy-lang.org/groovyVersion/html/gapi/groovy/lang/Closure.html"><classname>Closure</classname></ulink>[]</classname>'
    }

    def rendersLinkToExternalClass() {
        when:
        def link = renderer.link(type('some.other.Class'), listener)

        then:
        format(link) == '<classname>some.other.Class</classname>'
    }

    def rendersLinkToExternalClassArray() {
        when:
        def link = renderer.link(type('some.other.Class', true), listener)

        then:
        format(link) == '<classname><classname>some.other.Class</classname>[]</classname>'
    }

    def rendersLinkToParameterizedType() {
        def metaData = type('org.gradle.SomeClass')
        metaData.addTypeArg(type('Type1'))
        metaData.addTypeArg(type('Type2'))

        when:
        def link = renderer.link(metaData, listener)

        then:
        format(link) == '<classname><apilink class="org.gradle.SomeClass"/>&lt;<apilink class="Type1"/>, <apilink class="Type2"/>&gt;</classname>'
        _ * model.isKnownType('org.gradle.SomeClass') >> true
        _ * model.isKnownType('Type1') >> true
        _ * model.isKnownType('Type2') >> true
    }

    def rendersLinkToApiMethod() {
        def method = method('someMethod', 'org.gradle.SomeClass')

        when:
        def link = renderer.link(method, listener)

        then:
        format(link) == '<apilink class="org.gradle.SomeClass" method="someMethod()"/>'
        _ * model.isKnownType('org.gradle.SomeClass') >> true
    }

    def method(String name, String className) {
        MethodMetaData method = Mock()
        ClassMetaData ownerClass = Mock()
        _ * method.name >> name
        _ * method.overrideSignature >> "$name()"
        _ * method.ownerClass >> ownerClass
        _ * ownerClass.className >> className
        return method
    }

    def type(String name, boolean isArray = false) {
        TypeMetaData type = new TypeMetaData(name)
        if (isArray) {
            type.arrayDimensions = 1
        }
        return type
    }
}
