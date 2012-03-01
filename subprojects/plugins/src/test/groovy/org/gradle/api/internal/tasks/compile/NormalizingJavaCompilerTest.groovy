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

import spock.lang.Specification
import org.gradle.api.tasks.WorkResult
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.file.collections.SimpleFileCollection

class NormalizingJavaCompilerTest extends Specification {
    Compiler<JavaCompileSpec> target = Mock()
    JavaCompileSpec spec = new DefaultJavaCompileSpec()
    NormalizingJavaCompiler compiler = new NormalizingJavaCompiler(target)

    def setup() {
        spec.source = new SimpleFileCollection(new File("source.java"))
        spec.classpath = new SimpleFileCollection(new File("dependency.jar"))
    }

    def "delegates to target compiler after resolving source and classpath"() {
        WorkResult workResult = Mock()
        target.execute(spec) >> workResult

        when:
        def result = compiler.execute(spec)

        then:
        result == workResult
        !spec.source.is(old(spec.source))
        !spec.classpath.is(old(spec.classpath))
    }

    def "fails when a non-Java source file provided"() {
        spec.source = files(new File("source.txt"))

        when:
        compiler.execute(spec)

        then:
        0 * target._
        InvalidUserDataException e = thrown()
        e.message == 'Cannot compile non-Java source file \'source.txt\'.'
    }

    def "propagates compile failure when failOnError is true"() {
        def failure
        target.execute(spec) >> { throw failure = new CompilationFailedException() }

        spec.compileOptions.failOnError = true

        when:
        compiler.execute(spec)
        
        then:
        CompilationFailedException e = thrown()
        e == failure
    }

    def "ignores compile failure when failOnError is false"() {
        target.execute(spec) >> { throw new CompilationFailedException() }

        spec.compileOptions.failOnError = false

        when:
        def result = compiler.execute(spec)

        then:
        noExceptionThrown()
        !result.didWork
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

    def "resolves any GStrings that make it into custom compiler args"() {
        when:
        compiler.execute(spec)

        then:
        1 * target.execute(_) >> { JavaCompileSpec spec ->
            assert spec.compileOptions.compilerArgs.every { it instanceof String }
        }
    }
}
