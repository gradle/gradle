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

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.internal.Factory
import org.gradle.process.internal.ExecHandleFactory
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

import javax.tools.JavaCompiler

class DefaultJavaCompilerFactoryTest extends Specification {
    Factory<JavaCompiler> javaCompilerFinder = Mock()
    def factory = new DefaultJavaCompilerFactory({
        new File("daemon-work-dir")
    }, Mock(WorkerDaemonFactory), javaCompilerFinder, Mock(JavaForkOptionsFactory), Mock(ExecHandleFactory), Stub(AnnotationProcessorDetector), Stub(ClassPathRegistry))

    def "creates in-process compiler when JavaCompileSpec is provided"() {
        expect:
        def compiler = factory.create(JavaCompileSpec.class)
        compiler instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate instanceof JdkJavaCompiler
    }

    def "creates in-process compiler when JavaCompileSpec is provided and joint compilation"() {
        expect:
        def compiler = factory.createForJointCompilation(JavaCompileSpec.class)
        compiler instanceof JdkJavaCompiler
    }

    def "creates command line compiler when CommandLineJavaCompileSpec is provided"() {
        expect:
        def compiler = factory.create(TestCommandLineJavaSpec.class)
        compiler instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate instanceof CommandLineJavaCompiler
    }

    def "creates command line compiler when CommandLineJavaCompileSpec is provided and joint compilation"() {
        expect:
        def compiler = factory.createForJointCompilation(TestCommandLineJavaSpec)
        compiler instanceof CommandLineJavaCompiler
    }

    def "creates daemon compiler when ForkingJavaCompileSpec"() {
        expect:
        def compiler = factory.create(TestForkingJavaCompileSpec)
        compiler instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate instanceof DaemonJavaCompiler
        compiler.delegate.delegate.delegateClass == JdkJavaCompiler.class
    }

    def "creates in-process compiler when ForkingJavaCompileSpec is provided and joint compilation"() {
        expect:
        def compiler = factory.createForJointCompilation(TestForkingJavaCompileSpec)
        compiler instanceof JdkJavaCompiler
    }

    private static class TestCommandLineJavaSpec extends DefaultJavaCompileSpec implements CommandLineJavaCompileSpec {
    }

    private static class TestForkingJavaCompileSpec extends DefaultJavaCompileSpec implements ForkingJavaCompileSpec {
    }
}
