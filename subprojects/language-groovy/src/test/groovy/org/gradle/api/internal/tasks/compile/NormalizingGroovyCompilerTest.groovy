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
import groovy.transform.InheritConstructors
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompileOptions
import spock.lang.Specification
import spock.lang.Unroll

class NormalizingGroovyCompilerTest extends Specification {
    org.gradle.language.base.internal.compile.Compiler<GroovyJavaJointCompileSpec> target = Mock()
    DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpec()
    NormalizingGroovyCompiler compiler = new NormalizingGroovyCompiler(target)

    def setup() {
        spec.compileClasspath = [new File('Dep1.jar'), new File('Dep2.jar'), new File('Dep3.jar')]
        spec.groovyClasspath = spec.compileClasspath
        spec.source = files('House.scala', 'Person1.java', 'package.html', 'Person2.groovy')
        spec.destinationDir = new File("destinationDir")
        spec.compileOptions = new CompileOptions()
        spec.groovyCompileOptions = new GroovyCompileOptions()
    }

    def "silently excludes source files not ending in .java or .groovy by default"() {
        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.source.files == files('Person1.java', 'Person2.groovy').files
        }
    }

    def "excludes source files that have extension different from specified by fileExtensions option"() {
        spec.groovyCompileOptions.fileExtensions = ['html']

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.source.files == files('package.html').files
        }
    }

    def "propagates compile failure when both compileOptions.failOnError and groovyCompileOptions.failOnError are true"() {
        def failure
        target.execute(spec) >> { throw failure = new CompilationFailedException() }

        spec.compileOptions.failOnError = true
        spec.groovyCompileOptions.failOnError = true

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
        options << ['compileOptions', 'groovyCompileOptions']
    }

    private files(String... paths) {
        new TestFileCollection(paths.collect { new File(it) })
    }

    // file collection whose type is distinguishable from SimpleFileCollection
    @InheritConstructors
    static class TestFileCollection extends SimpleFileCollection {}
}
