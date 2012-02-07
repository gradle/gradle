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
package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.api.internal.tasks.compile.Compiler

import spock.lang.Specification
import org.gradle.api.internal.tasks.compile.SimpleWorkResult
import org.gradle.api.internal.tasks.compile.CompilationFailedException

class DefaultCompilerDaemonTest extends Specification {
    DefaultCompilerDaemon daemon = new DefaultCompilerDaemon()
    Compiler compiler = Mock(Compiler)
    
    def "executes the provided compiler"() {
        when:
        def compileResult = daemon.execute(compiler)

        then:
        1 * compiler.execute() >> new SimpleWorkResult(true)
        compileResult.success
    }

    def "returns whether the compiler did any work"() {
        compiler.execute() >> new SimpleWorkResult(didWork)

        when:
        def compileResult = daemon.execute(compiler)

        then:
        compileResult.success
        compileResult.didWork == didWork

        where: didWork << [true, false]
    }

    def "returns any exception thrown by the compiler"() {
        compiler.execute() >> { throw exception }
        
        when:
        def compileResult = daemon.execute(compiler)

        then:
        !compileResult.success
        compileResult.didWork
        compileResult.exception.is(exception)

        where:
        exception << [ new CompilationFailedException(), new RuntimeException("ouch") ]
    }
}
