/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.testing.internal.util.Specification

import static org.gradle.buildinit.plugins.internal.CppProjectInitDescriptor.NamespaceDeclaration.tab

class NamespaceDeclarationTest extends Specification {
    def "empty namespace declaration produces empty strings"() {
        given:
        def namespace = CppProjectInitDescriptor.NamespaceDeclaration.empty()

        expect:
        namespace.opening == ""
        namespace.closing == ""
        namespace.indent == ""
    }

    def "single component namespace produces appropriate strings"() {
        given:
        def namespace = CppProjectInitDescriptor.NamespaceDeclaration.from("foo")

        expect:
        namespace.opening == "\nnamespace foo {"
        namespace.closing == "\n}"
        namespace.indent == "${tab}"
    }

    def "complex namespace produces appropriate strings"() {
        given:
        def namespace = CppProjectInitDescriptor.NamespaceDeclaration.from("foo::bar::baz")

        expect:
        namespace.opening == "\nnamespace foo {\n${tab}namespace bar {\n${tab*2}namespace baz {"
        namespace.closing == "\n${tab*2}}\n${tab}}\n}"
        namespace.indent == "${tab*3}"
    }
}
