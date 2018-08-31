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

import org.gradle.api.file.ProjectLayout
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class CompileOptionsTest extends Specification {
    static final TEST_DEBUG_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']
    static final TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    CompileOptions compileOptions

    def setup()  {
        compileOptions = new CompileOptions(Stub(ProjectLayout), TestUtil.objectFactory())
        compileOptions.debugOptions = [optionMap: {TEST_DEBUG_OPTION_MAP}] as DebugOptions
        compileOptions.forkOptions = [optionMap: {TEST_FORK_OPTION_MAP}] as ForkOptions
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

    def "option map for debug options"() {
        Map optionMap = compileOptions.optionMap()
        expect:
        optionMap.subMap(TEST_DEBUG_OPTION_MAP.keySet()) == TEST_DEBUG_OPTION_MAP
        optionMap.subMap(TEST_FORK_OPTION_MAP.keySet()) == TEST_FORK_OPTION_MAP
    }

    @Unroll
    def "option map with nullable #option"() {
        expect:
        !compileOptions.optionMap().keySet().contains(option)

        when:
        compileOptions."$property" = "${property}Value"

        then:
        compileOptions.optionMap()[option] == "${property}Value"

        where:
        property             | option
        "encoding"           | "encoding"
        "extensionDirs"      | "extdirs"
    }

    @Unroll
    def "option map with true/false #property"() {
        when:
        compileOptions."$property" = true
        Map optionMap = compileOptions.optionMap()

        then:
        optionMap[option] == !(option == "nowarn")

        when:
        compileOptions."$property" = false
        optionMap = compileOptions.optionMap()

        then:
        optionMap[option] == (option == "nowarn")

        where:
        property      | option
        "failOnError" | "failOnError"
        "verbose"     | "verbose"
        "listFiles"   | "listFiles"
        "deprecation" | "deprecation"
        "warnings"    | "nowarn"
        "debug"       | "debug"

    }

    @Unroll
    def "with exclude #option from option map"() {
        compileOptions.compilerArgs = ["-value=something"]

        expect:
        !compileOptions.optionMap().containsKey(option)

        where:
        option << ['debugOptions', 'forkOptions', 'compilerArgs']
    }

    def "fork"() {
        compileOptions.fork = false
        boolean forkUseCalled = false

        compileOptions.forkOptions = [define: {Map args ->
            forkUseCalled = true
            assert args == TEST_FORK_OPTION_MAP
        }] as ForkOptions

        expect:
        compileOptions.fork(TEST_FORK_OPTION_MAP).is(compileOptions)
        compileOptions.fork
        forkUseCalled
    }

    def "debug"() {
        compileOptions.debug = false
        boolean debugUseCalled = false

        compileOptions.debugOptions = [define: {Map args ->
            debugUseCalled = true
            args == TEST_DEBUG_OPTION_MAP
        }] as DebugOptions

        expect:
        assert compileOptions.debug(TEST_DEBUG_OPTION_MAP).is(compileOptions)
        compileOptions.debug
        debugUseCalled
    }

    def "define"() {
        compileOptions.debug = false
        compileOptions.fork = false
        compileOptions.define(debug: true)

        expect:
        compileOptions.debug
        !compileOptions.fork
    }

    def "converts GStrings to Strings when getting all compiler arguments"() {
        given:
        compileOptions.compilerArgs << "Foo${23}"

        expect:
        compileOptions.allCompilerArgs.contains('Foo23')
    }
}
