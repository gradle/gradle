/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal

import spock.lang.Specification
import org.gradle.api.internal.resource.Resource
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.Transformer
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.TestScript

class ShortCircuitEmptyScriptCompilerTest extends Specification {
    final EmptyScriptGenerator emptyScriptGenerator = Mock()
    final ScriptClassCompiler target = Mock()
    final ScriptSource source = Mock()
    final Resource resource = Mock()
    final ClassLoader classLoader = Mock()
    final Transformer transformer = Mock()
    final ShortCircuitEmptyScriptCompiler compiler = new ShortCircuitEmptyScriptCompiler(target, emptyScriptGenerator)

    def setup() {
        _ * source.resource >> resource
    }

    def "returns empty script object when script contains only whitespace"() {
        given:
        _ * resource.text >> '  \n\t'

        when:
        def result = compiler.compile(source, classLoader, transformer, Script)

        then:
        result == TestScript
        1 * emptyScriptGenerator.generate(Script) >> TestScript
        0 * emptyScriptGenerator._
        0 * target._
    }

    def "compiles script when script contains anything other than whitespace"() {
        given:
        _ * resource.text >> 'some script'

        when:
        def result = compiler.compile(source, classLoader, transformer, Script)

        then:
        result == TestScript
        1 * target.compile(source, classLoader, transformer, Script) >> TestScript
        0 * emptyScriptGenerator._
        0 * target._
    }
}
