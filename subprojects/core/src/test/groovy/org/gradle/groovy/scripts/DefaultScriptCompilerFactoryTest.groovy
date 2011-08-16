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

import spock.lang.Specification
import org.gradle.logging.StandardOutputCapture
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory
import org.gradle.groovy.scripts.internal.ScriptClassCompiler

class DefaultScriptCompilerFactoryTest extends Specification {
    final ScriptRunnerFactory scriptRunnerFactory = Mock()
    final ScriptClassCompiler scriptClassCompiler = Mock()
    final ScriptSource source = Mock()
    final ScriptRunner<TestScript> runner = Mock()
    final ClassLoader classLoader = Mock()
    final Transformer transformer = Mock()
    final DefaultScriptCompilerFactory factory = new DefaultScriptCompilerFactory(scriptClassCompiler, scriptRunnerFactory)

    def "compiles script into class and wraps instance in script runner"() {
        when:
        def compiler = factory.createCompiler(source)
        compiler.classloader = classLoader
        compiler.transformer = transformer
        def result = compiler.compile(Script)

        then:
        result == runner
        1 * scriptClassCompiler.compile({it instanceof CachingScriptSource}, classLoader, transformer, Script) >> TestScript
        1 * scriptRunnerFactory.create({it instanceof TestScript}) >> runner
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
