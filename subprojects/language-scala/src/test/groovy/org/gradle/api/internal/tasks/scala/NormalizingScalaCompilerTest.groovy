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
package org.gradle.api.internal.tasks.scala

import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.scala.tasks.BaseScalaCompileOptions
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class NormalizingScalaCompilerTest extends Specification {
    Compiler<ScalaJavaJointCompileSpec> target = Mock()
    DefaultScalaJavaJointCompileSpec spec = new DefaultScalaJavaJointCompileSpec()
    NormalizingScalaCompiler compiler = new NormalizingScalaCompiler(target)

    def setup() {
        spec.destinationDir = new File("dest")
        spec.sourceFiles = files("Source1.java", "Source2.java", "Source3.java")
        spec.compileClasspath = [new File("Dep1.jar"), new File("Dep2.jar")]
        spec.zincClasspath = files("zinc.jar", "zinc-dep.jar")
        spec.compileOptions = new CompileOptions(TestUtil.objectFactory())
        spec.scalaCompileOptions = new BaseScalaCompileOptions()
    }

    def "delegates to target compiler after resolving source"() {
        def workResult = Mock(WorkResult)

        when:
        def result = compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == old(spec.sourceFiles)

            workResult
        }
        result == workResult
    }

    def "propagates compile failure when both compileOptions.failOnError and scalaCompileOptions.failOnError are true"() {
        def failure
        target.execute(spec) >> { throw failure = new CompilationFailedException() }

        spec.compileOptions.failOnError = true
        spec.scalaCompileOptions.failOnError = true

        when:
        compiler.execute(spec)

        then:
        CompilationFailedException e = thrown()
        e == failure
    }

    @Unroll
    def "ignores compile failure when one of #options dot failOnError is false"() {
        target.execute(spec) >> { throw new CompilationFailedException() }

        spec[options].failOnError = false

        when:
        def result = compiler.execute(spec)

        then:
        noExceptionThrown()
        !result.didWork

        where:
        options << ['compileOptions', 'scalaCompileOptions']
    }


    def "propagates other failure"() {
        def failure
        target.execute(spec) >> { throw failure = new RuntimeException() }

        when:
        compiler.execute(spec)

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "resolves any non-strings that make it into custom compiler args"() {
        spec.compileOptions.compilerArgs << "a dreaded ${"GString"}"
        spec.compileOptions.compilerArgs << 42
        assert !spec.compileOptions.compilerArgs.any { it instanceof String }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(_) >> { ScalaJavaJointCompileSpec spec ->
            assert spec.compileOptions.compilerArgs.every { it instanceof String }
        }
    }

    private files(String... paths) {
        paths.collect { new File(it) } as Set
    }
}

