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

import spock.lang.Specification

import org.gradle.internal.Factory
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.compile.fork.ForkingJavaCompiler
import org.gradle.api.internal.tasks.compile.daemon.DaemonJavaCompiler
import org.gradle.util.Jvm

class ForkingJavaCompilerFactoryTest extends Specification {
    def inProcessCompiler = Mock(JavaCompiler)
    def inProcessCompilerFactory = Mock(JavaCompilerFactory)
    def jvm = Mock(Jvm)
    def factory = new DefaultJavaCompilerFactory(Mock(ProjectInternal), Mock(Factory), inProcessCompilerFactory, jvm)
    def options = new CompileOptions()
    
    def setup() {
        jvm.isJava7() >> false
        inProcessCompilerFactory.create(_) >> inProcessCompiler
    }

    def "creates Ant compiler when useAnt=true"() {
        options.useAnt = true
        options.fork = fork
        
        expect:
        factory.create(options) instanceof AntJavaCompiler
        
        where: fork << [false, true]
    }
    
    def "creates in-process compiler when fork=false"() {
        options.useAnt = false
        options.fork = false

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.compiler.is(inProcessCompiler)
    }

    def "creates forking compiler when fork=true and useCompilerDaemon=false"() {
        options.useAnt = false
        options.fork = true
        options.forkOptions.useCompilerDaemon = false

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.compiler instanceof ForkingJavaCompiler
    }

    def "creates daemon compiler when fork=true and useCompilerDaemon=true"() {
        options.useAnt = false
        options.fork = true
        options.forkOptions.useCompilerDaemon = true

        expect:
        def compiler = factory.create(options)
        compiler instanceof NormalizingJavaCompiler
        compiler.compiler instanceof DaemonJavaCompiler
    }
}
