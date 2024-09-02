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

import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class CompileOptionsTest extends Specification {
    static final TEST_DEBUG_OPTION_MAP = [someDebugOption: 'someDebugOptionValue']

    CompileOptions compileOptions

    def setup()  {
        compileOptions = TestUtil.newInstance(CompileOptions, TestUtil.objectFactory())
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "default compile options"() {
        expect:
        compileOptions.debug.get()
        compileOptions.failOnError.get()
        compileOptions.warnings.get()

        !compileOptions.deprecation.get()
        !compileOptions.listFiles.get()
        !compileOptions.verbose.get()
        !compileOptions.fork.get()

        compileOptions.compilerArgs.empty
        compileOptions.encoding.getOrNull() == null
        compileOptions.bootstrapClasspath.isEmpty()
        compileOptions.extensionDirs.getOrNull() == null

        compileOptions.forkOptions != null
        compileOptions.debugOptions != null
    }

    def testFork() {
        compileOptions.fork = false
        assertNull(compileOptions.forkOptions.memoryMaximumSize.getOrNull())

        expect:
        compileOptions.fork([memoryMaximumSize: '1g'])
        assertTrue(compileOptions.fork.get())
        assertEquals(compileOptions.forkOptions.memoryMaximumSize.get(), '1g')
    }

    def "debug"() {
        compileOptions.debug.set(false)
        boolean debugUseCalled = false

        compileOptions.debugOptions = [define: {Map args ->
            debugUseCalled = true
            args == TEST_DEBUG_OPTION_MAP
        }] as DebugOptions

        expect:
        assert compileOptions.debug(TEST_DEBUG_OPTION_MAP).is(compileOptions)
        compileOptions.debug.get()
        debugUseCalled
    }

    def "define"() {
        compileOptions.debug.set(false)
        compileOptions.fork = false
        compileOptions.define(debug: true)

        expect:
        compileOptions.debug.get()
        !compileOptions.fork.get()
    }

    def "converts GStrings to Strings when getting all compiler arguments"() {
        given:
        compileOptions.compilerArgs << "Foo${23}"

        expect:
        compileOptions.allCompilerArgs.get().contains('Foo23')
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
}
