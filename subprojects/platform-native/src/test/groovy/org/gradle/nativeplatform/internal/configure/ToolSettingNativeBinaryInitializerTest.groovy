/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.internal.configure
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.language.base.internal.DefaultLanguageRegistry
import org.gradle.language.base.internal.LanguageRegistration
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.internal.DefaultTool
import spock.lang.Specification

class ToolSettingNativeBinaryInitializerTest extends Specification {
    def binary = Mock(ExtensionAwareNativeBinary)
    def languageRegistry = new DefaultLanguageRegistry()
    def language = Mock(LanguageRegistration)
    def initializer = new ToolSettingNativeBinaryInitializer(languageRegistry)

    def "does nothing with no languages"() {

        when:
        initializer.execute(binary)

        then:
        0 * _
    }

    def "does nothing when language has not tools registered"() {
        when:
        languageRegistry.add(language)

        language.binaryTools >> [:]

        and:
        initializer.execute(binary)

        then:
        0 * _
    }

    def "adds extension for each tool"() {
        def extensions = Mock(ExtensionContainer)
        when:
        languageRegistry.add(language)
        language.binaryTools >> [tool: DefaultTool, other: String]

        and:
        initializer.execute(binary)

        then:
        _ * binary.extensions >> extensions
        1 * extensions.create("tool", DefaultTool)
        1 * extensions.create("other", String)
        0 * _
    }

    private interface ExtensionAwareNativeBinary extends NativeBinarySpec, ExtensionAware {}
}
