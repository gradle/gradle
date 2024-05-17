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

abstract class AbstractIoTest extends Specification {

    protected abstract void executeWithException(Throwable exception)

    def "unchecked #type.simpleName is thrown directly"() {
        when:
        executeWithException(original)
        then:
        def ex = thrown type
        ex === original

        where:
        original << [
            new RuntimeException("lajos"),
            new Error("lajos"),
            new UncheckedIOException(new IOException("lajos"))
        ]
        type = original.class
    }

    def "IOException is thrown wrapped in UncheckedIOException"() {
        def original = new IOException("lajos")
        when:
        executeWithException(original)
        then:
        def ex = thrown UncheckedIOException
        ex.cause === original
    }
}
