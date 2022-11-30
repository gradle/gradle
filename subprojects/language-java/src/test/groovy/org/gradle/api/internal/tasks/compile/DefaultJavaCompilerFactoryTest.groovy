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
import org.gradle.process.internal.ExecHandleFactory
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

class DefaultJavaCompilerFactoryTest extends Specification {
    def factory = new DefaultJavaCompilerFactory({
        new File("daemon-work-dir")
    }, Mock(WorkerDaemonFactory), Mock(JavaForkOptionsFactory), Mock(ExecHandleFactory), Stub(AnnotationProcessorDetector), Stub(ClassPathRegistry), Stub(ActionExecutionSpecFactory))

    def "creates in-process compiler when JavaCompileSpec is provided"() {
        expect:
        def compiler = factory.create(JavaCompileSpec.class)
        compiler instanceof ModuleApplicationNameWritingCompiler
        compiler.delegate instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate.delegate instanceof JdkJavaCompiler
    }

    def "creates command line compiler when CommandLineJavaCompileSpec is provided"() {
        expect:
        def compiler = factory.create(TestCommandLineJavaSpec.class)
        compiler instanceof ModuleApplicationNameWritingCompiler
        compiler.delegate instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate.delegate instanceof CommandLineJavaCompiler
    }

    def "creates daemon compiler when ForkingJavaCompileSpec"() {
        expect:
        def compiler = factory.create(TestForkingJavaCompileSpec)
        compiler instanceof ModuleApplicationNameWritingCompiler
        compiler.delegate instanceof AnnotationProcessorDiscoveringCompiler
        compiler.delegate.delegate instanceof NormalizingJavaCompiler
        compiler.delegate.delegate.delegate instanceof DaemonJavaCompiler
        compiler.delegate.delegate.delegate.compilerClass == JdkJavaCompiler.class
    }

    private static class TestCommandLineJavaSpec extends DefaultJavaCompileSpec implements CommandLineJavaCompileSpec {
        private final File executable;

        private TestCommandLineJavaSpec(File executable) {
            this.executable = executable;
        }

        @Override
        public File getExecutable() {
            return executable;
        }
    }

    private static class TestForkingJavaCompileSpec extends DefaultJavaCompileSpec implements ForkingJavaCompileSpec {
        private final File javaHome;

        private TestForkingJavaCompileSpec(File javaHome) {
            this.javaHome = javaHome;
        }

        @Override
        public File getJavaHome() {
            return javaHome;
        }
    }
}
