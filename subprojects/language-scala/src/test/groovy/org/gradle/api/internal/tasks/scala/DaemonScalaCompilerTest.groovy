/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.tasks.scala

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.compile.MinimalJavaCompileOptions
import org.gradle.api.tasks.compile.ForkOptions
import org.gradle.api.tasks.scala.ScalaForkOptions
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.language.scala.tasks.BaseScalaCompileOptions
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.process.internal.JavaForkOptionsInternal
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class DaemonScalaCompilerTest extends Specification {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()
    def workingDirectory = new File(".").absoluteFile
    def delegateClass = ZincScalaCompilerFacade.class
    def delegateParameters = [] as Object[]
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    ScalaJavaJointCompileSpec spec = Mock(ScalaJavaJointCompileSpec)
    def javaCompileOptions = Mock(MinimalJavaCompileOptions)
    def scalaCompileOptions = Mock(BaseScalaCompileOptions)
    def javaForkOptions = Mock(ForkOptions)
    def scalaForkOptions = Mock(ScalaForkOptions)
    def forkOptionsFactory = new TestForkOptionsFactory(TestFiles.execFactory())
    def classpathRegistry = Mock(ClassPathRegistry)
    def classLoaderRegistry = Mock(ClassLoaderRegistry)
    def actionExecutionSpecFactory = Mock(ActionExecutionSpecFactory)

    def setup(){
        _ * spec.getCompileOptions() >> javaCompileOptions
        _ * spec.getScalaCompileOptions() >> scalaCompileOptions
        _ * javaCompileOptions.getForkOptions() >> javaForkOptions
        _ * scalaCompileOptions.getForkOptions() >> scalaForkOptions
        _ * javaForkOptions.jvmArgs >> []
        _ * scalaForkOptions.jvmArgs >> []
        _ * classpathRegistry.getClassPath(_) >> new DefaultClassPath()
        _ * classLoaderRegistry.gradleApiFilterSpec >> Mock(FilteringClassLoader.Spec)
    }

    def "passes compile classpath to daemon options"() {
        given:
        def classpath = someClasspath()
        def compiler = new DaemonScalaCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, classpath,
            forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        then:
        daemonForkOptions.getClassLoaderStructure().spec.classpath == someClasspath().collect { it.toURI().toURL() }
    }

    def "applies fork settings to daemon options"(){
        given:
        def compiler = new DaemonScalaCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, someClasspath(), forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        1 * javaForkOptions.getMemoryInitialSize() >> "256m"
        1 * javaForkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getMinHeapSize() == "256m"
        daemonForkOptions.javaForkOptions.getMaxHeapSize() == "512m"
    }

    def "applies scala fork settings to daemon options"(){
        given:
        def compiler = new DaemonScalaCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, someClasspath(), forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        1 * scalaForkOptions.getMemoryInitialSize() >> "256m"
        1 * scalaForkOptions.getMemoryMaximumSize() >> "512m"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getMinHeapSize() == "256m"
        daemonForkOptions.javaForkOptions.getMaxHeapSize() == "512m"
    }

    def "sets executable on daemon options"(){
        given:
        def compiler = new DaemonScalaCompiler(workingDirectory, delegateClass, delegateParameters, workerDaemonFactory, someClasspath(), forkOptionsFactory, classpathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
        when:
        1 * javaForkOptions.getExecutable() >> "custom/java"
        then:
        def daemonForkOptions = compiler.toDaemonForkOptions(spec)
        daemonForkOptions.javaForkOptions.getExecutable() == "custom/java"
    }

    def someClasspath() {
        [new File("foo"), new File("bar")]
    }

    class TestForkOptionsFactory implements JavaForkOptionsFactory {
        private final JavaForkOptionsFactory delegate

        TestForkOptionsFactory(JavaForkOptionsFactory delegate) {
            this.delegate = delegate
        }

        @Override
        JavaForkOptionsInternal newDecoratedJavaForkOptions() {
            return newJavaForkOptions()
        }

        @Override
        JavaForkOptionsInternal newJavaForkOptions() {
            def forkOptions = delegate.newJavaForkOptions()
            forkOptions.setWorkingDir(temporaryFolder.root)
            return forkOptions
        }

        @Override
        JavaForkOptionsInternal immutableCopy(JavaForkOptionsInternal options) {
            return delegate.immutableCopy(options)
        }
    }
}
