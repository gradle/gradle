/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.scala.compile.internal

import org.gradle.api.tasks.scala.ScalaCompileOptions
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

@Subject(ScalaCompileOptions)
class ScalaCompileOptionsTest extends Specification {

    def 'additional parameters are empty by default'() {
        ScalaCompileOptions scalaCompileOptions = TestUtil.newInstance(ScalaCompileOptions)

        expect:
        scalaCompileOptions.additionalParameters != null
        scalaCompileOptions.additionalParameters.isEmpty()
        scalaCompileOptions.compilerArgumentProviders.get().isEmpty()
        scalaCompileOptions.allCompilerArgs.get().isEmpty()
    }

    def 'can append to additional parameters #description'() {
        ScalaCompileOptions scalaCompileOptions = TestUtil.newInstance(ScalaCompileOptions)

        when:
        action(scalaCompileOptions)
        scalaCompileOptions.additionalParameters.add("-some-flag")

        then:
        scalaCompileOptions.additionalParameters == ["-some-flag"]

        where:
        description                       | action
        "by default"                      | {}
        "after setting to null"           | { it.additionalParameters = null }
        "after setting to immutable list" | { it.additionalParameters = [].asImmutable() }
    }

    @Issue('https://github.com/gradle/gradle/issues/39899')
    def 'replacing additional parameters preserves provider arguments and their order'() {
        given:
        def options = TestUtil.newInstance(ScalaCompileOptions)
        options.additionalParameters = ['-replaced']
        options.compilerArgumentProviders.add({ ['-first', 'value', '-repeated', '-repeated'] } as CommandLineArgumentProvider)
        options.compilerArgumentProviders.add({ [] } as CommandLineArgumentProvider)
        options.compilerArgumentProviders.add({ ['-last'] } as CommandLineArgumentProvider)

        when:
        options.additionalParameters = parameters

        then:
        options.allCompilerArgs.get() == expectedRawParameters + ['-first', 'value', '-repeated', '-repeated', '-last']
        options.additionalParameters == expectedRawParameters

        where:
        parameters                         | expectedRawParameters
        ['-application'].asImmutable()      | ['-application']
        []                                 | []
        null                               | []
    }

    def 'combined arguments are calculated lazily'() {
        given:
        def options = TestUtil.newInstance(ScalaCompileOptions)
        def argumentProvider = Mock(CommandLineArgumentProvider)
        options.compilerArgumentProviders.add(argumentProvider)

        when:
        def arguments = options.allCompilerArgs

        then:
        0 * argumentProvider.asArguments()

        when:
        def value = arguments.get()

        then:
        1 * argumentProvider.asArguments() >> ['-contributed']
        value == ['-contributed']
    }

    def 'combined argument provider reflects changes to both argument sources'() {
        given:
        def options = TestUtil.newInstance(ScalaCompileOptions)
        def contributed = ['-first']
        options.compilerArgumentProviders.add({ contributed } as CommandLineArgumentProvider)
        def arguments = options.allCompilerArgs

        expect:
        arguments.get() == ['-first']

        when:
        options.additionalParameters = ['-application']
        contributed.add('-second')
        options.compilerArgumentProviders.add({ ['-third'] } as CommandLineArgumentProvider)

        then:
        arguments.get() == ['-application', '-first', '-second', '-third']
    }
}
