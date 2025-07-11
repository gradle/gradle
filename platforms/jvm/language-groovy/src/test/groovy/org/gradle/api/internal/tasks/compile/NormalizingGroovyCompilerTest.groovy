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

import org.gradle.api.tasks.compile.GroovyCompileOptions
import org.gradle.util.TestUtil
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

class NormalizingGroovyCompilerTest extends Specification {

    org.gradle.language.base.internal.compile.Compiler<GroovyJavaJointCompileSpec> target = Mock()
    NormalizingGroovyCompiler compiler = new NormalizingGroovyCompiler(target)

    def setup() {
    }

    def "silently excludes source files not ending in .java or .groovy by default"() {
        def spec = getSpec(groovyOptionsOf {}) {
            sourceFiles = files('House.scala', 'Person1.java', 'package.html', 'Person2.groovy')
        }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files('Person1.java', 'Person2.groovy')
        }
    }

    def "excludes source files that have extension different from specified by fileExtensions option"() {
        def spec = getSpec(groovyOptionsOf {
            fileExtensions = ['html']
        }) {
            sourceFiles = files('House.scala', 'Person1.java', 'package.html', 'Person2.groovy')
        }

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files('package.html')
        }
    }

    def "propagates compile failure when both compileOptions.failOnError and groovyCompileOptions.failOnError are true"() {
        def javaOptions = TestJavaOptions.of {
            failOnError = true
        }
        def groovyOptions = groovyOptionsOf {
            failOnError = true
        }
        def spec = getSpec(javaOptions, groovyOptions)

        def failure
        target.execute(spec) >> { throw failure = new CompilationFailedException() }

        when:
        compiler.execute(spec)

        then:
        CompilationFailedException e = thrown()
        e == failure
    }

    def "ignores compile failure when one of java or groovy failOnError is false"() {
        target.execute(spec) >> { throw new CompilationFailedException() }

        when:
        def result = compiler.execute(spec)

        then:
        noExceptionThrown()
        !result.didWork

        where:
        spec << [
            getSpec(TestJavaOptions.of { failOnError = false }, groovyOptionsOf { failOnError = true }),
            getSpec(TestJavaOptions.of { failOnError = true }, groovyOptionsOf { failOnError = false }),
        ]
    }

    private files(String... paths) {
        paths.collect { new File(it) } as Set
    }

    private static MinimalGroovyCompileOptions groovyOptionsOf(@DelegatesTo(GroovyCompileOptions) Closure<?> action) {
        def groovyCompileOptions = ConfigureUtil.configure(action, TestUtil.newInstance(GroovyCompileOptions))
        return MinimalGroovyCompileOptionsConverter.toMinimalGroovyCompileOptions(groovyCompileOptions)
    }

    private DefaultGroovyJavaJointCompileSpec getSpec(MinimalGroovyCompileOptions groovyCompileOptions, @DelegatesTo(DefaultGroovyJavaJointCompileSpec) Closure<?> action = {}) {
        return getSpec(TestJavaOptions.of { }, groovyCompileOptions, action)
    }

    private static DefaultGroovyJavaJointCompileSpec getSpec(MinimalJavaCompileOptions javaCompileOptions, MinimalGroovyCompileOptions groovyCompileOptions, @DelegatesTo(DefaultGroovyJavaJointCompileSpec) Closure<?> action = {}) {
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpec()
        spec.sourceFiles = []
        spec.compileOptions = javaCompileOptions
        spec.groovyCompileOptions = groovyCompileOptions
        return ConfigureUtil.configure(action, spec)
    }

}
