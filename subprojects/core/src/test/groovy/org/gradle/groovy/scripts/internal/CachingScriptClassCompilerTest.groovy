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
package org.gradle.groovy.scripts.internal

import org.gradle.api.Action
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.TestScript
import org.gradle.groovy.scripts.Transformer
import spock.lang.Specification

class CachingScriptClassCompilerTest extends Specification {
    private final ScriptClassCompiler target = Mock()
    private final CachingScriptClassCompiler compiler = new CachingScriptClassCompiler(target)
    private final CompiledScript<?, ?> compiledScript = Mock(CompiledScript)
    private final String classpathClosureName = "buildscript"
    final verifier = Mock(Action)
    def classLoaderId = Mock(ClassLoaderId)

    def "caches the script class for a given script class and classloader and transformer and baseclass"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        CompileOperation<?> transformer = operation()

        when:
        def c1 = compiler.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
        def c2 = compiler.compile(script2, parentClassLoader, classLoaderId, transformer, Script.class, verifier)

        then:
        c1 == c2
        1 * target.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier) >> compiledScript
        0 * target._
    }

    def "does not cache script class for different script class"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('other')
        ClassLoader parentClassLoader = Mock()
        CompileOperation<?> transformer = operation()

        when:
        compiler.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
        compiler.compile(script2, parentClassLoader, classLoaderId, transformer, Script.class, verifier)

        then:
        1 * target.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
        1 * target.compile(script2, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
    }

    def "does not cache script class for different transformers"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        CompileOperation<?> transformer1 = operation('t1')
        CompileOperation<?> transformer2 = operation('t2')

        when:
        compiler.compile(script1, parentClassLoader, classLoaderId, transformer1, Script.class, verifier)
        compiler.compile(script2, parentClassLoader, classLoaderId, transformer2, Script.class, verifier)

        then:
        1 * target.compile(script1, parentClassLoader, classLoaderId, transformer1, Script.class, verifier)
        1 * target.compile(script2, parentClassLoader, classLoaderId, transformer2, Script.class, verifier)
    }

    def "does not cache script class for different classloaders"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader1 = Mock()
        ClassLoader parentClassLoader2 = Mock()
        CompileOperation<?> transformer = operation()

        when:
        compiler.compile(script1, parentClassLoader1, classLoaderId, transformer, Script.class, verifier)
        compiler.compile(script2, parentClassLoader2, classLoaderId, transformer, Script.class, verifier)

        then:
        1 * target.compile(script1, parentClassLoader1, classLoaderId, transformer, Script.class, verifier)
        1 * target.compile(script2, parentClassLoader2, classLoaderId, transformer, Script.class, verifier)
    }

    def "does not cache script class for different base classes"() {
        ScriptSource script1 = scriptSource('script')
        ScriptSource script2 = scriptSource('script')
        ClassLoader parentClassLoader = Mock()
        CompileOperation<?> transformer = operation()

        when:
        compiler.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
        compiler.compile(script2, parentClassLoader, classLoaderId, transformer, TestScript.class, verifier)

        then:
        1 * target.compile(script1, parentClassLoader, classLoaderId, transformer, Script.class, verifier)
        1 * target.compile(script2, parentClassLoader, classLoaderId, transformer, TestScript.class, verifier)
    }

    def scriptSource(String className = 'script') {
        ScriptSource script = Mock()
        _ * script.className >> className
        script
    }

    def operation(String id = 'id') {
        CompileOperation<?> operation = Mock()
        Transformer transformer = Mock()
        operation.id >> id
        operation.transformer >> transformer
        operation
    }
}
