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
package org.gradle.groovy.scripts

import org.gradle.api.Action
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.internal.CompileOperation
import org.gradle.groovy.scripts.internal.CompiledScript
import org.gradle.groovy.scripts.internal.ScriptClassCompiler
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.resource.TextResource
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class DefaultScriptCompilerFactoryTest extends Specification {
    final ScriptRunnerFactory scriptRunnerFactory = Mock()
    final ScriptClassCompiler scriptClassCompiler = Mock()
    final ScriptSource source = Mock() {
        getFileName() >> "script.file"
        getResource() >> Stub(TextResource)
    }
    final ScriptRunner<TestScript, ?> runner = Mock()
    final ClassLoader classLoader = Mock()
    final ClassLoaderScope targetScope = Stub() {
        createChild(_) >> Stub(ClassLoaderScope)
        getExportClassLoader() >> classLoader
    }
    final CompileOperation<?> operation = Mock() {
        getId() >> "id"
    }
    final CompiledScript<TestScript, ?> compiledScript = Mock() {
        loadClass() >> TestScript
    }
    final verifier = Mock(Action)
    final DefaultScriptCompilerFactory factory = new DefaultScriptCompilerFactory(scriptClassCompiler, scriptRunnerFactory)

    def "compiles script into class and wraps instance in script runner"() {
        given:
        def target = new Object()

        when:
        def compiler = factory.createCompiler(source)
        def result = compiler.compile(Script, target, targetScope, operation, verifier)

        then:
        result == runner

        1 * scriptClassCompiler.compile({ it instanceof CachingScriptSource}, Script, target, targetScope, operation, verifier) >> compiledScript
        1 * scriptRunnerFactory.create(compiledScript, { it instanceof CachingScriptSource}, classLoader) >> runner
        0 * scriptRunnerFactory._
        0 * scriptClassCompiler._
    }
}

class TestScript extends Script {
    @Override
    StandardOutputCapture getStandardOutputCapture() {
    }

    @Override
    void init(Object target, ServiceRegistry services) {
    }

    Object run() {
    }
}
