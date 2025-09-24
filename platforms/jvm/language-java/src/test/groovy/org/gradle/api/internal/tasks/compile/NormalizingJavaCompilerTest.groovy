/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import org.gradle.api.tasks.WorkResult
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

class NormalizingJavaCompilerTest extends Specification {
    org.gradle.language.base.internal.compile.Compiler<JavaCompileSpec> target = Mock()
    NormalizingJavaCompiler compiler = new NormalizingJavaCompiler(target)

    def setup() {
    }

    def "replaces iterable sources with immutable set"() {
        def spec = getSpec(TestJavaOptions.of()) {
            sourceFiles = ["Person1.java", "Person2.java"].collect { new File(it) }
        }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files("Person1.java", "Person2.java")
            assert spec.sourceFiles instanceof ImmutableSet
        }
    }

    def "silently excludes source files not ending in .java"() {
        def spec = getSpec(TestJavaOptions.of()) {
            sourceFiles = files("House.scala", "Person1.java", "package.html", "Person2.java")
        }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files("Person1.java", "Person2.java")
        }
    }

    def "delegates to target compiler after resolving source and processor path"() {
        WorkResult workResult = Mock()
        def spec = getSpec(TestJavaOptions.of()) {
            sourceFiles = files("Source1.java", "Source2.java", "Source3.java")
        }

        when:
        def result = compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == old(spec.sourceFiles)
            workResult
        }
        result == workResult
    }

    def "propagates compile failure when failOnError is true"() {
        def spec = getSpec(TestJavaOptions.of {
            failOnError = true
        })

        def failure
        target.execute(spec) >> { throw failure = new CompilationFailedException() }


        when:
        compiler.execute(spec)

        then:
        CompilationFailedException e = thrown()
        e == failure
    }

    def "ignores compile failure when failOnError is false"() {
        def spec = getSpec(TestJavaOptions.of {
            failOnError = false
        })

        target.execute(spec) >> { throw new CompilationFailedException() }

        when:
        def result = compiler.execute(spec)

        then:
        noExceptionThrown()
        !result.didWork
    }

    def "propagates other failure"() {
        def spec = getSpec(TestJavaOptions.of())

        def failure
        target.execute(spec) >> { throw failure = new RuntimeException() }

        when:
        compiler.execute(spec)

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "resolves any non-strings that make it into custom compiler args"() {
        def spec = getSpec(TestJavaOptions.of {
            def args = ["a dreaded ${"GString"}", 42] as List<String>
            assert !args.any { it instanceof String }
            compilerArgs = args
        })

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(_) >> { JavaCompileSpec s ->
            assert s.compileOptions.compilerArgs.every { it instanceof String }
        }
    }

    private files(String... paths) {
        paths.collect { new File(it) } as Set
    }

    private static DefaultJavaCompileSpec getSpec(MinimalJavaCompileOptions compileOptions, @DelegatesTo(DefaultGroovyJavaJointCompileSpec) Closure<?> action = {}) {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpec()
        spec.sourceFiles = []
        spec.compileOptions = compileOptions
        return ConfigureUtil.configure(action, spec)
    }
}
