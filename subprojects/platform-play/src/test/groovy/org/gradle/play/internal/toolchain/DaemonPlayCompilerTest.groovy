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

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.compile.BaseForkOptions
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.play.internal.routes.RoutesCompiler
import org.gradle.play.internal.spec.PlayCompileSpec
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

class DaemonPlayCompilerTest extends Specification {

    def workingDirectory = new File(".").absoluteFile
    def delegateClass = RoutesCompiler.class
    def delegateParameters = [] as Object[]
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def spec = Mock(PlayCompileSpec)
    def forkOptions = Mock(BaseForkOptions)
    def forkOptionsFactory = TestFiles.execFactory()
    def classpathRegistry = Mock(ClassPathRegistry)
    def classLoaderRegistry = Mock(ClassLoaderRegistry)
    def actionExecutionSpecFactory = Mock(ActionExecutionSpecFactory)

    def setup(){
        _ * spec.getForkOptions() >> forkOptions
        _ * forkOptions.jvmArgs >> []
        _ * classpathRegistry.getClassPath(_) >> new DefaultClassPath()
        _ * classLoaderRegistry.gradleApiFilterSpec >> Mock(FilteringClassLoader.Spec)
    }

    def "passes compile classpath to daemon options"() {
        given:
        def classpath = someClasspath()
        def compiler = new DaemonPlayCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, classpath, forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        then:
        daemonForkOptions.getClassLoaderStructure().spec.classpath == someClasspath().collect { it.toURI().toURL() }
    }

    def "applies fork settings to daemon options"(){
        given:
        def compiler = new DaemonPlayCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, someClasspath(), forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        1 * forkOptions.getMemoryInitialSize() >> "256m"
        1 * forkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getMinHeapSize() == "256m"
        daemonForkOptions.javaForkOptions.getMaxHeapSize() == "512m"
    }

    def someClasspath() {
        [new File("foo"), new File("bar")]
    }
}
