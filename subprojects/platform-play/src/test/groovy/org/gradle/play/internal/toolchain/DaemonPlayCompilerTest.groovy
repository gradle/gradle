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

package org.gradle.play.internal.toolchain


import org.gradle.api.tasks.compile.BaseForkOptions
import org.gradle.internal.file.PathToFileResolver
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.play.internal.spec.PlayCompileSpec
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

class DaemonPlayCompilerTest extends Specification {

    def workingDirectory = Mock(File)
    def delegate = Mock(Compiler)
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def spec = Mock(PlayCompileSpec)
    def forkOptions = Mock(BaseForkOptions)
    def forkOptionsFactory = Mock(JavaForkOptionsFactory)

    def setup(){
        _ * forkOptionsFactory.newJavaForkOptions() >> new DefaultJavaForkOptions(Mock(PathToFileResolver))
        _ * forkOptionsFactory.immutableCopy(_) >> { JavaForkOptions options -> options }
        _ * spec.getForkOptions() >> forkOptions
        _ * forkOptions.jvmArgs >> []
    }

    def "passes compile classpath and packages to daemon options"() {
        given:
        def classpath = someClasspath()
        def packages = ["foo", "bar"]
        def compiler = new DaemonPlayCompiler(workingDirectory, delegate, workerDaemonFactory, classpath, packages, forkOptionsFactory)
        when:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        then:
        daemonForkOptions.getClasspath() == classpath
        daemonForkOptions.getSharedPackages() == packages
    }

    def "applies fork settings to daemon options"(){
        given:
        def compiler = new DaemonPlayCompiler(workingDirectory, delegate, workerDaemonFactory, someClasspath(), [], forkOptionsFactory)
        when:
        1 * forkOptions.getMemoryInitialSize() >> "256m"
        1 * forkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getMinHeapSize() == "256m"
        daemonForkOptions.javaForkOptions.getMaxHeapSize() == "512m"
    }

    def someClasspath() {
        [Mock(File), Mock(File)]
    }
}
