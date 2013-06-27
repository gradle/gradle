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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.compile.daemon.DaemonJavaCompiler
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.internal.Factory

import spock.lang.Specification

class DefaultJavaCompilerFactoryTest extends Specification {
    def inProcessCompiler = Mock(Compiler)
    def inProcessCompilerFactory = Mock(JavaCompilerFactory)
    def factory = new DefaultJavaCompilerFactory(Mock(ProjectInternal), Mock(Factory), inProcessCompilerFactory)
    def options = new CompileOptions()
    
    def setup() {
        inProcessCompilerFactory.create(_) >> inProcessCompiler
    }

    def "creates Ant compiler when useAnt=true"() {
        options.useAnt = true
        options.fork = fork
        
        expect:
        factory.create(options) instanceof AntJavaCompiler
        
        where: fork << [false, true]
    }

    def "falls back to Ant compiler when options.compiler is set"() {
        options.useAnt = false
        options.compiler = "jikes"

        expect:
        factory.create(options) instanceof AntJavaCompiler
    }
    
    def "creates in-process compiler when fork=false"() {
        options.useAnt = false
        options.fork = false

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.delegate.is(inProcessCompiler)
    }

    def "creates command line compiler when fork=true and forkOptions.executable is set"() {
        options.useAnt = false
        options.fork = true
        options.forkOptions.executable = "/path/to/javac"

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.delegate instanceof CommandLineJavaCompiler
    }

    def "creates daemon compiler when fork=true"() {
        options.useAnt = false
        options.fork = true

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.delegate instanceof DaemonJavaCompiler
    }
}
