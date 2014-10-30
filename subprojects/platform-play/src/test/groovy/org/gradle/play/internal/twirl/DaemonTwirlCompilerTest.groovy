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

package org.gradle.play.internal.twirl

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory
import spock.lang.Specification

class DaemonTwirlCompilerTest extends Specification {
    def workingDirectory = Mock(File)
    def delegate = Mock(TwirlCompiler)
    def compilerDaemonFactory = Mock(CompilerDaemonFactory)
    def spec = Mock(TwirlCompileSpec)

    def "shares play compiler package"() {
        given:
        def compiler = new DaemonTwirlCompiler(workingDirectory, delegate, compilerDaemonFactory, Collections.emptyList())
        when:
        def options = compiler.toDaemonOptions(spec);
        then:
        options.getSharedPackages().asList().contains("play.templates")
        options.getSharedPackages().asList().contains("play.twirl.compiler")
    }

    def "passes compileclasspath to daemon options"() {
        given:
        def classpath = someClasspath()
        def compiler = new DaemonTwirlCompiler(workingDirectory, delegate, compilerDaemonFactory, classpath)
        when:
        def options = compiler.toDaemonOptions(spec);
        then:
        options.getClasspath() == classpath
    }

    def someClasspath() {
       [Mock(File), Mock(File)]
    }
}
