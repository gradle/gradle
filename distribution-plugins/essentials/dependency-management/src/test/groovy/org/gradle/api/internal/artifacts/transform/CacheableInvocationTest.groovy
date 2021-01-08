/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.internal.Try
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class CacheableInvocationTest extends Specification {

    def "composed invocation only creates the second invocation once"() {
        def creationCount = new AtomicInteger(0)

        def composed = first.flatMap { Integer input ->
            creationCount.incrementAndGet()
            return CacheableInvocation.nonCached { Try.successful(input + 25) }
        }

        when:
        def result = composed.invoke()
        composed.getCachedResult()
        then:
        result.get() == 30
        creationCount.get() == 1

        where:
        first << [CacheableInvocation.nonCached { Try.successful(5) }, CacheableInvocation.cached(Try.successful(5))]
    }
}
