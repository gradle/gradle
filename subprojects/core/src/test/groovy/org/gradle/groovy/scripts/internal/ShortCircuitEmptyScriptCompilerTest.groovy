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

import org.gradle.api.Action
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.resource.Resource
import spock.lang.Specification

class ShortCircuitEmptyScriptCompilerTest extends Specification {
    final ScriptClassCompiler target = Mock()
    final ScriptSource source = Mock()
    final Resource resource = Mock()
    final ClassLoader classLoader = Mock()
    final CompileOperation<?> operation = Mock()
    final Action verifier = Mock()
    final classLoaderCache = Mock(ClassLoaderCache)
    final ShortCircuitEmptyScriptCompiler compiler = new ShortCircuitEmptyScriptCompiler(target, classLoaderCache)
    def loaderId = ClassLoaderIds.buildScript(source.getFileName(), operation.getId())

    def setup() {
        _ * source.resource >> resource
    }

    def "returns empty script object when script contains only whitespace"() {
        given:
        def metadata = "metadata"
        _ * resource.text >> '  \n\t'
        _ * operation.extractedData >> metadata


        when:
        def compiledScript = compiler.compile(source, classLoader, loaderId, operation, Script, verifier)

        then:
        !compiledScript.runDoesSomething
        !compiledScript.hasMethods
        compiledScript.data == metadata

        and:
        0 * target._
    }

    def "compiles script when script contains anything other than whitespace"() {
        given:
        _ * resource.text >> 'some script'
        CompiledScript<?> compiledScript = Mock()

        when:
        def result = compiler.compile(source, classLoader, ClassLoaderIds.buildScript(source.getFileName(), operation.getId()), operation, Script, verifier)

        then:
        result == compiledScript
        1 * target.compile(source, classLoader, ClassLoaderIds.buildScript(source.getFileName(), operation.getId()), operation, Script, verifier) >> compiledScript
        0 * target._
        0 * classLoaderCache._
    }
}
