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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompileOptions
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import org.gradle.util.TestUtil
import spock.lang.Specification

class NormalizingGroovyCompilerTest extends Specification {
    org.gradle.language.base.internal.compile.Compiler<GroovyJavaJointCompileSpec> target = Mock()
    DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpec()
    NormalizingGroovyCompiler compiler = new NormalizingGroovyCompiler(target)

    def setup() {
        spec.compileClasspath = [new File('Dep1.jar'), new File('Dep2.jar'), new File('Dep3.jar')]
        spec.groovyClasspath = spec.compileClasspath
        spec.sourceFiles = files('House.scala', 'Person1.java', 'package.html', 'Person2.groovy')
        spec.destinationDir = new File("destinationDir")
        spec.compileOptions = TestUtil.newInstance(CompileOptions.class, TestUtil.objectFactory())
        ServiceLookup services = new DefaultServiceRegistry().add(ObjectFactory, TestUtil.objectFactory())
        spec.groovyCompileOptions = new MinimalGroovyCompileOptions(TestUtil.instantiatorFactory().decorateLenient(services).newInstance(GroovyCompileOptions.class))
    }

    def "silently excludes source files not ending in .java or .groovy by default"() {
        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files('Person1.java', 'Person2.groovy')
        }
    }

    def "excludes source files that have extension different from specified by fileExtensions option"() {
        spec.groovyCompileOptions.fileExtensions = ['html']

        when:
        compiler.execute(spec)

        then:
        1 * target.execute(spec) >> {
            assert spec.sourceFiles == files('package.html')
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
        paths.collect { new File(it) } as Set
    }
}
