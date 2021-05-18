/*
 * Copyright 2007-2008 the original author or authors.
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

import spock.lang.Specification

import static org.junit.Assert.*

class GroovyCompileOptionsTest extends Specification {
    static final Map TEST_FORK_OPTION_MAP = [someForkOption: 'someForkOptionValue']

    GroovyCompileOptions compileOptions

    def setup() {
        compileOptions = new GroovyCompileOptions()
    }

    def "default compile options"() {
        expect:
        assertTrue(compileOptions.failOnError)
        assertFalse(compileOptions.listFiles)
        assertFalse(compileOptions.verbose)
        assertTrue(compileOptions.fork)
        assertEquals(['java', 'groovy'], compileOptions.fileExtensions)
        assertEquals('UTF-8', compileOptions.encoding)
        assertNotNull(compileOptions.forkOptions)
        assertNull(compileOptions.configurationScript)
        assertFalse(compileOptions.javaAnnotationProcessing)
        assertFalse(compileOptions.parameters)
    }

    def "fork"() {
        def forkOptions = Mock(GroovyForkOptions)
        1 * forkOptions.define(TEST_FORK_OPTION_MAP)

        compileOptions.fork = false
        compileOptions.forkOptions = forkOptions

        expect:
        assert compileOptions.fork(TEST_FORK_OPTION_MAP).is(compileOptions)
        assertTrue(compileOptions.fork)
    }

    def "define"() {
        compileOptions.verbose = false
        compileOptions.encoding = 'xxxx'
        compileOptions.fork = false
        compileOptions.parameters = true

        expect:
        compileOptions.define( encoding: 'encoding')
        assertEquals('encoding', compileOptions.encoding)
        assertFalse(compileOptions.verbose)
        assertFalse(compileOptions.fork)
        assertTrue(compileOptions.parameters)
    }
}
