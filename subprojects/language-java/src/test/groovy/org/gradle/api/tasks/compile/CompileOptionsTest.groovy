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

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

class CompileOptionsTest extends Specification {
    static final TEST_DEBUG_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']
    static final TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    CompileOptions compileOptions

    def setup()  {
        compileOptions = new CompileOptions(TestUtil.objectFactory())
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
        compileOptions.bootClasspath == null
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

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "define"() {
        compileOptions.debug = false
        compileOptions.bootClasspath = 'xxxx'
        compileOptions.fork = false
        compileOptions.define(debug: true, bootClasspath: null)

        expect:
        compileOptions.debug
        compileOptions.bootClasspath == null
        compileOptions.bootstrapClasspath == null
        !compileOptions.fork
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "boot classpath is reflected via deprecated property"() {
        def bootstrapClasspath = Mock(FileCollectionInternal)

        when:
        compileOptions.bootstrapClasspath = bootstrapClasspath

        then:
        compileOptions.bootstrapClasspath == bootstrapClasspath

        when:
        def deprecatedPath = compileOptions.bootClasspath

        then:
        deprecatedPath == "resolved"
        1 * bootstrapClasspath.getAsPath() >> "resolved"
        0 * _
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "setting deprecated bootClasspath resets bootstrapClasspath"() {
        given:
        compileOptions.bootstrapClasspath = new SimpleFileCollection(new File("lib1.jar"))

        when:
        compileOptions.bootClasspath = "lib2.jar"

        then:
        compileOptions.bootClasspath == "lib2.jar"
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "setting deprecated bootClasspath sets bootstrapClasspath"() {
        given:
        compileOptions.bootClasspath = "lib2.jar"

        expect:
        compileOptions.bootstrapClasspath.files as List == [new File("lib2.jar")]
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "setting bootstrapClasspath sets deprecated bootClasspath"() {
        given:
        compileOptions.bootClasspath = "lib1.jar"

        when:
        compileOptions.bootstrapClasspath = new SimpleFileCollection(new File("lib2.jar"))

        then:
        compileOptions.bootClasspath == "lib2.jar"
    }
}
