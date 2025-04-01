/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

class CompileOptionsTest extends Specification {

    CompileOptions compileOptions

    def setup()  {
        compileOptions = TestUtil.newInstance(CompileOptions, TestUtil.objectFactory())
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "default compile options"() {
        expect:
        compileOptions.debug
        compileOptions.failOnError
        compileOptions.warnings

        !compileOptions.deprecation
        !compileOptions.listFiles
        !compileOptions.verbose
        !compileOptions.fork

        compileOptions.compilerArgs.empty
        compileOptions.encoding == null
        compileOptions.bootstrapClasspath == null
        compileOptions.extensionDirs == null

        compileOptions.forkOptions != null
        compileOptions.debugOptions != null
    }

    def "converts GStrings to Strings when getting all compiler arguments"() {
        given:
        compileOptions.compilerArgs << "Foo${23}"

        expect:
        compileOptions.allCompilerArgs.contains('Foo23')
    }

    void "forkOptions closure"() {
        AtomicReference<ForkOptions> forkOptions = new AtomicReference<ForkOptions>()
        compileOptions.forkOptions(forkOptions::set)

        expect:
        compileOptions.forkOptions == forkOptions.get()
    }

    void "debugOptions closure"() {
        AtomicReference<DebugOptions> debugOptions = new AtomicReference<DebugOptions>()
        compileOptions.debugOptions(debugOptions::set)

        expect:
        compileOptions.debugOptions == debugOptions.get()
    }

    @Issue("https://github.com/gradle/gradle/issues/32606")
    def "getAllCompilerArgs() returns only Strings"() {
        given:
        def commandLineArgumentProvider = new CommandLineArgumentProvider() {
            @Override
            Iterable<String> asArguments() {
                return ["${'make this a GString'}"]
            }
        }
        compileOptions.compilerArgumentProviders.add(commandLineArgumentProvider)

        expect:
        commandLineArgumentProvider.asArguments().iterator().next() instanceof GString
        compileOptions.allCompilerArgs.size() == 1
        compileOptions.allCompilerArgs[0] instanceof String
    }

}
