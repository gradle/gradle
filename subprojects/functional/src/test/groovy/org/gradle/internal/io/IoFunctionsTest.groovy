/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.io

import spock.lang.Specification

import static org.gradle.internal.io.IoFunctions.wrapConsumer
import static org.gradle.internal.io.IoFunctions.wrapFunction
import static org.gradle.internal.io.IoFunctions.wrapSupplier

class IoFunctionsTest extends Specification {
    def "consumer is executed when it doesn't throw"() {
        when:
        String encountered = null
        wrapConsumer({ String payload -> encountered = payload }).accept("lajos")
        then:
        encountered == "lajos"
    }

    def "consumer can throw RuntimeException directly"() {
        when:
        wrapConsumer({ String payload -> throw new RuntimeException(payload) }).accept("lajos")
        then:
        def runtimeEx = thrown RuntimeException
        runtimeEx.message == "lajos"
    }

    def "consumer can throw IOException, but it gets wrapped"() {
        when:
        wrapConsumer({ String payload -> throw new IOException(payload) }).accept("lajos")
        then:
        def ioEx = thrown UncheckedIOException
        ioEx.cause instanceof IOException
        ioEx.cause.message == "lajos"
    }

    def "supplier is executed when it doesn't throw"() {
        expect:
        wrapSupplier({ "lajos" }).get() == "lajos"
    }

    def "supplier can throw RuntimeException directly"() {
        when:
        wrapSupplier({ throw new RuntimeException("lajos") }).get()
        then:
        def runtimeEx = thrown RuntimeException
        runtimeEx.message == "lajos"
    }

    def "supplier can throw IOException, but it gets wrapped"() {
        when:
        wrapSupplier({ throw new IOException("lajos") }).get()
        then:
        def ioEx = thrown UncheckedIOException
        ioEx.cause instanceof IOException
        ioEx.cause.message == "lajos"
    }

    def "function is executed when it doesn't throw"() {
        expect:
        wrapFunction({ String t -> "wrapped:$t" }).apply("lajos") == "wrapped:lajos"
    }

    def "function can throw RuntimeException directly"() {
        when:
        wrapFunction({ String t -> throw new RuntimeException(t) }).apply("lajos")
        then:
        def runtimeEx = thrown RuntimeException
        runtimeEx.message == "lajos"
    }

    def "function can throw IOException, but it gets wrapped"() {
        when:
        wrapFunction({ String t -> throw new IOException(t) }).apply("lajos")
        then:
        def ioEx = thrown UncheckedIOException
        ioEx.cause instanceof IOException
        ioEx.cause.message == "lajos"
    }
}
