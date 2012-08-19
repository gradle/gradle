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

import org.gradle.api.tasks.WorkResult
import org.gradle.api.internal.file.collections.SimpleFileCollection

import groovy.transform.InheritConstructors
import org.gradle.api.tasks.compile.CompileOptions
import spock.lang.Specification

class NormalizingJavaCompilerTest extends Specification {
    Compiler<JavaCompileSpec> target = Mock()
    DefaultJavaCompileSpec spec = new DefaultJavaCompileSpec()
    NormalizingJavaCompiler compiler = new NormalizingJavaCompiler(target)

    def setup() {
        spec.source = files("Source1.java", "Source2.java", "Source3.java")
        spec.classpath = files("Dep1.jar", "Dep2.jar", "Dep3.jar")
        spec.compileOptions = new CompileOptions()
    }

    def "delegates to target compiler after resolving source and classpath"() {
        WorkResult workResult = Mock()

        when:
        def result = compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.source.getClass() == SimpleFileCollection
            assert spec.source.files == old(spec.source.files)
            assert spec.classpath.getClass() == SimpleFileCollection
            assert spec.classpath.files == old(spec.classpath.files)
            workResult
        }
        result == workResult
    }

    def "silently excludes source files not ending in .java"() {
        spec.source = files("House.scala", "Person1.java", "package.html", "Person2.java")

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.source.files == files("Person1.java", "Person2.java").files
        }
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

    def "resolves any non-strings that make it into custom compiler args"() {
        spec.compileOptions.compilerArgs << "a dreaded ${"GString"}"
        spec.compileOptions.compilerArgs << 42
        assert !spec.compileOptions.compilerArgs.any { it instanceof String }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(_) >> { JavaCompileSpec spec ->
            assert spec.compileOptions.compilerArgs.every { it instanceof String }
        }
    }

    private files(String... paths) {
        new TestFileCollection(paths.collect { new File(it) })
    }

    // file collection whose type is distinguishable from SimpleFileCollection
    @InheritConstructors
    static class TestFileCollection extends SimpleFileCollection {}
}
